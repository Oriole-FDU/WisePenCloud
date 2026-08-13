package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.dto.req.MessagePublishRequest;
import com.oriole.wisepen.user.api.domain.dto.res.MessageInfoResponse;
import com.oriole.wisepen.user.api.enums.MessageDeliveryScope;
import com.oriole.wisepen.user.api.enums.MessageType;
import com.oriole.wisepen.user.domain.entity.MessageEntity;
import com.oriole.wisepen.user.domain.entity.MessageRecipientEntity;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.domain.entity.GroupEntity;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.MessageMapper;
import com.oriole.wisepen.user.mapper.MessageRecipientMapper;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.service.IMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements IMessageService {

    private final MessageMapper messageMapper;
    private final MessageRecipientMapper messageRecipientMapper;
    private final UserMapper userMapper;
    private final GroupMapper groupMapper;

    private static final Pattern USER_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{USER:(\\d+)}}");
    private static final Pattern GROUP_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{GROUP:(\\d+)}}");

    @Value("${wisepen.user.message-dedup-window-minutes:60}")
    private long messageDedupWindowMinutes;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishMessage(MessagePublishRequest req) {
        // DIRECT 必须指定接收用户
        if (MessageDeliveryScope.DIRECT.equals(req.getDeliveryScope()) && CollectionUtils.isEmpty(req.getReceiverUserIds())) {
            throw new ServiceException(UserError.MESSAGE_RECEIVER_REQUIRED);
        }
        // ALL_USERS 消息必须是系统消息
        if (MessageDeliveryScope.ALL_USERS.equals(req.getDeliveryScope()) && !MessageType.SYSTEM.equals(req.getMessageType())) {
            throw new ServiceException(UserError.MESSAGE_DELIVERY_SCOPE_INVALID);
        }

        MessageEntity message;
        if (req.getSourceService() != null && req.getBizTraceId() != null) {
            LambdaQueryWrapper<MessageEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MessageEntity::getSourceService, req.getSourceService()).eq(MessageEntity::getBizTraceId, req.getBizTraceId());
            message = messageMapper.selectOne(wrapper);
            if (message != null) {
                // 存在则直接返回
                return;
            }
        }

        List<Long> receiverUserIds = req.getReceiverUserIds();
        String messageHash = req.getMessageHash();
        if (MessageDeliveryScope.DIRECT.equals(req.getDeliveryScope())) {
            // 找最近窗口内同 hash 的消息
            LocalDateTime dedupStartTime = LocalDateTime.now().minusMinutes(messageDedupWindowMinutes);
            LambdaQueryWrapper<MessageEntity> duplicateMessageWrapper = new LambdaQueryWrapper<>();
            duplicateMessageWrapper.eq(MessageEntity::getMessageHash, messageHash)
                    .ge(MessageEntity::getCreateTime, dedupStartTime);
            List<Long> duplicateMessageIds = messageMapper.selectList(duplicateMessageWrapper).stream()
                    .map(MessageEntity::getMessageId)
                    .toList();
            if (!duplicateMessageIds.isEmpty()) {
                // 查这些消息是否已经投递给当前 receiver
                LambdaQueryWrapper<MessageRecipientEntity> duplicateRecipientWrapper = new LambdaQueryWrapper<>();
                duplicateRecipientWrapper.in(MessageRecipientEntity::getMessageId, duplicateMessageIds)
                        .in(MessageRecipientEntity::getUserId, receiverUserIds)
                        .eq(MessageRecipientEntity::getDeliveryScope, MessageDeliveryScope.DIRECT)
                        .isNull(MessageRecipientEntity::getDeleteTime);
                Set<Long> duplicateReceiverUserIds = messageRecipientMapper.selectList(duplicateRecipientWrapper).stream()
                        .map(MessageRecipientEntity::getUserId)
                        .collect(Collectors.toSet());
                // 命中的 receiver 剔除
                receiverUserIds = receiverUserIds.stream()
                        .filter(receiverUserId -> !duplicateReceiverUserIds.contains(receiverUserId))
                        .toList();
            }
            if (receiverUserIds.isEmpty()) {
                return;
            }
        }

        // 不存在则插入消息，保留 bizTraceId 幂等记录
        message = BeanUtil.copyProperties(req, MessageEntity.class);
        message.setMessageHash(messageHash);
        messageMapper.insert(message);

        if (MessageDeliveryScope.ALL_USERS.equals(message.getDeliveryScope())) {
            return; // ALL_USERS 消息为懒加载，无需后续处理
        }

        MessageEntity finalMessage = message;
        List<MessageRecipientEntity> recipients = receiverUserIds.stream()
                .map(receiverUserId -> MessageRecipientEntity.builder()
                        .id(IdWorker.getId()).messageId(finalMessage.getMessageId()).deliveryScope(MessageDeliveryScope.DIRECT)
                        .userId(receiverUserId).createTime(finalMessage.getCreateTime())
                        .build())
                .collect(Collectors.toList());
        if (recipients.isEmpty()) {
            return;
        }
        // 批量插入
        messageRecipientMapper.insertBatch(recipients);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageR<MessageInfoResponse> listMessages(Long userId, Integer page, Integer size) {
        // 同步全员消息
        syncAllUserMessages(userId);

        Page<MessageRecipientEntity> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<MessageRecipientEntity> recipientWrapper = new LambdaQueryWrapper<>();
        recipientWrapper.eq(MessageRecipientEntity::getUserId, userId)
                .isNull(MessageRecipientEntity::getDeleteTime)
                .orderByDesc(MessageRecipientEntity::getCreateTime)
                .orderByDesc(MessageRecipientEntity::getMessageId);

        IPage<MessageRecipientEntity> recipientPage = messageRecipientMapper.selectPage(pageParam, recipientWrapper);
        PageR<MessageInfoResponse> pageR = new PageR<>(recipientPage.getTotal(), page, size);
        if (CollectionUtils.isEmpty(recipientPage.getRecords())) {
            return pageR;
        }

        // 查询对应 Message 详情
        List<Long> messageIds = recipientPage.getRecords().stream().map(MessageRecipientEntity::getMessageId).toList();
        LambdaQueryWrapper<MessageEntity> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.in(MessageEntity::getMessageId, messageIds);
        Map<Long, MessageEntity> messageMap = messageMapper.selectList(messageWrapper).stream()
                .collect(Collectors.toMap(MessageEntity::getMessageId, Function.identity()));

        // 组装返回
        List<MessageInfoResponse> records = recipientPage.getRecords().stream()
                .map(recipient -> {
                    MessageInfoResponse response = BeanUtil.copyProperties(messageMap.get(recipient.getMessageId()), MessageInfoResponse.class);
                    response.setReadTime(recipient.getReadTime());
                    return response;
                }).toList();
        Set<Long> templateUserIds = new HashSet<>();
        Set<Long> templateGroupIds = new HashSet<>();
        records.forEach(response -> {
            collectTemplateIds(response.getTitle(), USER_PLACEHOLDER_PATTERN, templateUserIds);
            collectTemplateIds(response.getContent(), USER_PLACEHOLDER_PATTERN, templateUserIds);
            collectTemplateIds(response.getTitle(), GROUP_PLACEHOLDER_PATTERN, templateGroupIds);
            collectTemplateIds(response.getContent(), GROUP_PLACEHOLDER_PATTERN, templateGroupIds);
        });
        Map<Long, UserEntity> templateUserMap = templateUserIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(templateUserIds).stream()
                .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));
        Map<Long, GroupEntity> templateGroupMap = templateGroupIds.isEmpty() ? Map.of() : groupMapper.selectBatchIds(templateGroupIds).stream()
                .collect(Collectors.toMap(GroupEntity::getGroupId, Function.identity()));
        records.forEach(response -> {
            response.setTitle(renderMessageTemplate(response.getTitle(), templateUserMap, templateGroupMap));
            response.setContent(renderMessageTemplate(response.getContent(), templateUserMap, templateGroupMap));
        });
        pageR.addAll(records);
        return pageR;
    }

    private void collectTemplateIds(String text, Pattern pattern, Set<Long> ids) {
        if (text == null) return;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            ids.add(Long.valueOf(matcher.group(1)));
        }
    }

    private String renderMessageTemplate(String text, Map<Long, UserEntity> userMap, Map<Long, GroupEntity> groupMap) {
        if (text == null) {
            return null;
        }
        Matcher userMatcher = USER_PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer rendered = new StringBuffer();
        while (userMatcher.find()) {
            Long userId = Long.valueOf(userMatcher.group(1));
            UserEntity user = userMap.get(userId);
            String userName = "用户 " + (user == null ? userId : user.getNickname());
            userMatcher.appendReplacement(rendered, Matcher.quoteReplacement(userName));
        }
        userMatcher.appendTail(rendered);

        Matcher groupMatcher = GROUP_PLACEHOLDER_PATTERN.matcher(rendered.toString());
        StringBuffer groupRendered = new StringBuffer();
        while (groupMatcher.find()) {
            Long groupId = Long.valueOf(groupMatcher.group(1));
            GroupEntity group = groupMap.get(groupId);
            String groupName = "小组" + (group == null ? groupId : group.getGroupName());
            groupMatcher.appendReplacement(groupRendered, Matcher.quoteReplacement(groupName));
        }
        groupMatcher.appendTail(groupRendered);
        return groupRendered.toString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long getUnreadMessageCount(Long userId) {
        syncAllUserMessages(userId);

        LambdaQueryWrapper<MessageRecipientEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageRecipientEntity::getUserId, userId)
                .isNull(MessageRecipientEntity::getDeleteTime)
                .isNull(MessageRecipientEntity::getReadTime);
        return messageRecipientMapper.selectCount(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readMessages(Long userId, List<Long> messageIds) {
        LambdaUpdateWrapper<MessageRecipientEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MessageRecipientEntity::getUserId, userId)
                .in(MessageRecipientEntity::getMessageId, messageIds)
                .isNull(MessageRecipientEntity::getDeleteTime)
                .isNull(MessageRecipientEntity::getReadTime)
                .set(MessageRecipientEntity::getReadTime, LocalDateTime.now());
        messageRecipientMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readAllMessages(Long userId) {
        LambdaUpdateWrapper<MessageRecipientEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MessageRecipientEntity::getUserId, userId)
                .isNull(MessageRecipientEntity::getDeleteTime)
                .isNull(MessageRecipientEntity::getReadTime)
                .set(MessageRecipientEntity::getReadTime, LocalDateTime.now());
        messageRecipientMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMessage(Long userId, Long messageId) {
        LambdaUpdateWrapper<MessageRecipientEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MessageRecipientEntity::getId, messageId)
                .isNull(MessageRecipientEntity::getDeleteTime)
                .set(MessageRecipientEntity::getDeleteTime, LocalDateTime.now());
        messageRecipientMapper.update(null, wrapper);
    }

    private void syncAllUserMessages(Long userId) {
        // 懒同步 ALL_USERS 全员消息
        // 查询当前用户上次同步的最近一条全员消息
        LambdaQueryWrapper<MessageRecipientEntity> latestWrapper = new LambdaQueryWrapper<>();
        latestWrapper.eq(MessageRecipientEntity::getUserId, userId)
                .eq(MessageRecipientEntity::getDeliveryScope, MessageDeliveryScope.ALL_USERS)
                .orderByDesc(MessageRecipientEntity::getCreateTime)
                .orderByDesc(MessageRecipientEntity::getMessageId)
                .last("LIMIT 1");
        MessageRecipientEntity latestALLUserMessage = messageRecipientMapper.selectOne(latestWrapper);

        LocalDateTime syncAllUserMessageStartTime;
        if (latestALLUserMessage == null) { // 从未同步过，使用用户注册时间作为起始
            UserEntity user = userMapper.selectById(userId);
            syncAllUserMessageStartTime = user.getCreateTime();
        } else { // 同步过，使用上次同步的最近一条消息时间作为起始
            syncAllUserMessageStartTime = latestALLUserMessage.getCreateTime();
        }
        // 获取需要同步的全员消息
        // 同一时间戳边界上的消息会被排除，即两条全员系统消息的 create_time 完全相同时可能会漏掉消息
        // 已假设全员系统消息的 create_time 不能相同
        LambdaQueryWrapper<MessageEntity> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.eq(MessageEntity::getDeliveryScope, MessageDeliveryScope.ALL_USERS)
                .eq(MessageEntity::getMessageType, MessageType.SYSTEM)
                .gt(MessageEntity::getCreateTime, syncAllUserMessageStartTime);
        List<MessageEntity> messages = messageMapper.selectList(messageWrapper);
        // 如果有这样的全员消息，马上同步
        if (!CollectionUtils.isEmpty(messages)) {
            List<MessageRecipientEntity> recipients = messages.stream()
                    .map(message -> MessageRecipientEntity.builder()
                            .id(IdWorker.getId()).messageId(message.getMessageId()).deliveryScope(MessageDeliveryScope.ALL_USERS)
                            .userId(userId).createTime(message.getCreateTime())
                            .build())
                    .collect(Collectors.toList());
            messageRecipientMapper.insertBatch(recipients);
        }
    }
}
