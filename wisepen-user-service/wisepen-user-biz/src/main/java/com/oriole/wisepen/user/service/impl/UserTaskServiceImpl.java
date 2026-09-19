package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.user.api.domain.dto.req.MessagePublishRequest;
import com.oriole.wisepen.user.api.domain.dto.res.UserTaskStatusResponse;
import com.oriole.wisepen.user.api.enums.MessageDeliveryScope;
import com.oriole.wisepen.user.api.enums.MessageType;
import com.oriole.wisepen.user.api.enums.RewardType;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import com.oriole.wisepen.user.api.enums.UserTaskType;
import com.oriole.wisepen.user.api.enums.WalletTransactionType;
import com.oriole.wisepen.user.domain.entity.UserTaskRecordEntity;
import com.oriole.wisepen.user.mapper.UserTaskRecordMapper;
import com.oriole.wisepen.user.service.IMessageService;
import com.oriole.wisepen.user.service.IUserTaskService;
import com.oriole.wisepen.user.service.IWalletService;
import com.oriole.wisepen.user.task.UserTaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class UserTaskServiceImpl implements IUserTaskService {

    private final UserTaskRecordMapper userTaskRecordMapper;
    private final IWalletService walletService;
    private final IMessageService messageService;
    private final Map<UserTaskCode, UserTaskHandler> handlerMap = new EnumMap<>(UserTaskCode.class);

    // Spring 会注入所有 UserTaskHandler
    public UserTaskServiceImpl(UserTaskRecordMapper userTaskRecordMapper, IWalletService walletService,
                               IMessageService messageService, List<UserTaskHandler> handlers) {
        this.userTaskRecordMapper = userTaskRecordMapper;
        this.walletService = walletService;
        this.messageService = messageService;
        for (UserTaskHandler handler : handlers) {
            for (UserTaskCode taskCode : handler.getTaskCodes()) {
                handlerMap.put(taskCode, handler);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object complete(Long userId, UserTaskCode taskCode, Long operatorId, String meta) {
        // 查询任务处理器
        UserTaskHandler handler = handlerMap.get(taskCode);
        if (handler == null) {
            // 任务枚举未找到实现，直接抛 IllegalArgumentException
            throw new IllegalArgumentException("Unsupported user task: " + taskCode);
        }

        UserTaskHandler.UserTaskContext context = UserTaskHandler.UserTaskContext.builder()
                .operatorId(operatorId == null ? userId : operatorId) // 操作者
                .meta(meta) // 业务说明
                .build();

        // 未启用任务，跳过
        if (!handler.isEnabled(taskCode)) {
            return handler.buildSkippedResponse(userId, taskCode, context, null);
        }

        // 获取任务限制
        // ONCE 只允许完成一次
        // DAILY_MAX_TIMES 某个自然日窗口内最多 N 次
        // UNCHECKED 不检查
        UserTaskHandler.UserTaskLimit limit = handler.getLimit(taskCode);

        // 查询是否存在满足限制条件的记录
        UserTaskRecordEntity existingRecord = findBlockingRecord(userId, taskCode, limit);
        if (existingRecord != null) { // 被限制完成，跳过
            log.info("user task skipped by limit. userId={} taskCode={} limitType={}",
                    userId, taskCode.getValue(), limit.getType());
            return handler.buildSkippedResponse(userId, taskCode, context, existingRecord);
        }

        // 计算任务奖励
        UserTaskHandler.UserTaskReward reward = handler.calculateReward(userId, taskCode, context);
        if (reward == null) { // 任务奖励为空，跳过
            return handler.buildSkippedResponse(userId, taskCode, context, null);
        }

        // RewardType.NONE 为完成任务但不发奖励
        RewardType rewardType = reward.getRewardType() == null ? RewardType.NONE : reward.getRewardType();
        int rewardAmount = reward.getRewardAmount() == null ? 0 : reward.getRewardAmount();

        // 如果这个任务声明要发奖励，奖励数量必须大于 0，否则跳过（不允许倒扣）
        if (!RewardType.NONE.equals(rewardType) && rewardAmount <= 0) {
            return handler.buildSkippedResponse(userId, taskCode, context, null);
        }

        String taskMeta = handler.buildMeta(userId, taskCode, context, reward);
        String walletTraceId = RewardType.NONE.equals(rewardType) ? null : IdUtil.randomUUID(); // RewardType.NONE 不涉及奖励，无钱包流水 ID
        UserTaskRecordEntity record = UserTaskRecordEntity.builder()
                .userId(userId)
                .taskCode(taskCode)
                .rewardType(rewardType)
                .rewardAmount(rewardAmount)
                .walletTraceId(walletTraceId)
                .meta(taskMeta)
                .completeTime(LocalDateTime.now())
                .build();
        userTaskRecordMapper.insert(record); // 插入任务记录

        grantReward(userId, rewardType, rewardAmount, walletTraceId, context.getOperatorId(), taskMeta); // 发放奖励
        publishRewardMessage(userId, taskCode, context, handler, record, reward);
        return handler.buildCompletedResponse(userId, taskCode, context, record, reward);
    }

    @Override
    public List<UserTaskStatusResponse> listTaskStatus(Long userId) {
        // 遍历所有已注册任务，构建当前用户的任务状态
        return handlerMap.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getValue()))
                .map(entry -> buildTaskStatus(userId, entry.getKey(), entry.getValue()))
                .toList();
    }

    private UserTaskStatusResponse buildTaskStatus(Long userId, UserTaskCode taskCode, UserTaskHandler handler) {
        // 获取任务限制和奖励预览
        UserTaskHandler.UserTaskLimit limit = handler.getLimit(taskCode);
        UserTaskStatusResponse.UserTaskRewardPreview rewardPreview = handler.previewReward(taskCode);
        boolean enabled = handler.isEnabled(taskCode);

        if (limit == null || UserTaskHandler.UserTaskLimit.LimitType.UNCHECKED.equals(limit.getType())) {
            // 不限制完成次数时，当前只表达是否可继续完成
            return UserTaskStatusResponse.builder()
                    .taskCode(taskCode)
                    .taskType(UserTaskType.UNCHECKED)
                    .enabled(enabled)
                    .canComplete(enabled)
                    .rewardPreview(rewardPreview)
                    .build();
        }

        if (UserTaskHandler.UserTaskLimit.LimitType.ONCE.equals(limit.getType())) {
            // 一次性任务按历史记录判断是否完成
            UserTaskRecordEntity record = userTaskRecordMapper.selectOne(Wrappers.<UserTaskRecordEntity>lambdaQuery()
                    .eq(UserTaskRecordEntity::getUserId, userId)
                    .eq(UserTaskRecordEntity::getTaskCode, taskCode)
                    .orderByDesc(UserTaskRecordEntity::getCompleteTime)
                    .last("LIMIT 1"));
            boolean completed = record != null;
            return UserTaskStatusResponse.builder()
                    .taskCode(taskCode)
                    .taskType(UserTaskType.ONCE)
                    .enabled(enabled)
                    .canComplete(enabled && !completed)
                    .rewardPreview(rewardPreview)
                    .once(UserTaskStatusResponse.OnceTaskStatus.builder()
                            .completed(completed)
                            .completeTime(completed ? record.getCompleteTime() : null)
                            .build())
                    .build();
        }

        if (UserTaskHandler.UserTaskLimit.LimitType.DAILY_MAX_TIMES.equals(limit.getType())) {
            // 周期任务只统计当前窗口内的完成次数
            int maxTimes = limit.getMaxTimes() == null ? 1 : limit.getMaxTimes();
            int refreshCycleDays = limit.getRefreshCycleDays() == null ? 1 : limit.getRefreshCycleDays();
            LocalDateTime windowStart = LocalDate.now()
                    .minusDays(Math.max(refreshCycleDays, 1) - 1L)
                    .atStartOfDay();
            LocalDateTime windowEnd = windowStart.plusDays(Math.max(refreshCycleDays, 1));
            Long completedTimes = userTaskRecordMapper.selectCount(Wrappers.<UserTaskRecordEntity>lambdaQuery()
                    .eq(UserTaskRecordEntity::getUserId, userId)
                    .eq(UserTaskRecordEntity::getTaskCode, taskCode)
                    .ge(UserTaskRecordEntity::getCompleteTime, windowStart)
                    .lt(UserTaskRecordEntity::getCompleteTime, windowEnd));
            UserTaskRecordEntity lastRecord = userTaskRecordMapper.selectOne(Wrappers.<UserTaskRecordEntity>lambdaQuery()
                    .eq(UserTaskRecordEntity::getUserId, userId)
                    .eq(UserTaskRecordEntity::getTaskCode, taskCode)
                    .ge(UserTaskRecordEntity::getCompleteTime, windowStart)
                    .lt(UserTaskRecordEntity::getCompleteTime, windowEnd)
                    .orderByDesc(UserTaskRecordEntity::getCompleteTime)
                    .last("LIMIT 1"));
            return UserTaskStatusResponse.builder()
                    .taskCode(taskCode)
                    .taskType(UserTaskType.PERIODIC)
                    .enabled(enabled)
                    .canComplete(enabled && completedTimes < maxTimes)
                    .rewardPreview(rewardPreview)
                    .periodic(UserTaskStatusResponse.PeriodicTaskStatus.builder()
                            .completedTimes(completedTimes.intValue())
                            .maxTimes(maxTimes)
                            .windowStart(windowStart)
                            .windowEnd(windowEnd)
                            .lastCompleteTime(lastRecord == null ? null : lastRecord.getCompleteTime())
                            .build())
                    .build();
        }

        throw new IllegalArgumentException("Unsupported task limit: " + limit.getType());
    }

    private UserTaskRecordEntity findBlockingRecord(Long userId, UserTaskCode taskCode, UserTaskHandler.UserTaskLimit limit) {
        // 如果任务没有限制，或者明确不检查，直接允许完成
        if (limit == null || UserTaskHandler.UserTaskLimit.LimitType.UNCHECKED.equals(limit.getType())) {
            return null;
        }

        if (UserTaskHandler.UserTaskLimit.LimitType.ONCE.equals(limit.getType())) { // ONCE 表示这个任务一人只能完成一次
            // 查到，说明这个用户已经做过这个任务，不能再做；否则可以继续
            return userTaskRecordMapper.selectOne(Wrappers.<UserTaskRecordEntity>lambdaQuery()
                    .eq(UserTaskRecordEntity::getUserId, userId)
                    .eq(UserTaskRecordEntity::getTaskCode, taskCode)
                    .last("LIMIT 1"));
        }

        if (UserTaskHandler.UserTaskLimit.LimitType.DAILY_MAX_TIMES.equals(limit.getType())) { // DAILY_MAX_TIMES 表示一个刷新周期内最多完成 N 次
            int maxTimes = limit.getMaxTimes() == null ? 1 : limit.getMaxTimes();
            int refreshCycleDays = limit.getRefreshCycleDays() == null ? 1 : limit.getRefreshCycleDays();
            LocalDateTime windowStart = LocalDate.now()
                    .minusDays(Math.max(refreshCycleDays, 1) - 1L)
                    .atStartOfDay(); // 算窗口起点
            Long completedTimes = userTaskRecordMapper.selectCount(Wrappers.<UserTaskRecordEntity>lambdaQuery()
                    .eq(UserTaskRecordEntity::getUserId, userId)
                    .eq(UserTaskRecordEntity::getTaskCode, taskCode)
                    .ge(UserTaskRecordEntity::getCompleteTime, windowStart)); // 统计这个窗口内完成了几次
            if (completedTimes < maxTimes) { // 如果次数还没达到上限
                return null;
            }
            return userTaskRecordMapper.selectOne(Wrappers.<UserTaskRecordEntity>lambdaQuery()
                    .eq(UserTaskRecordEntity::getUserId, userId)
                    .eq(UserTaskRecordEntity::getTaskCode, taskCode)
                    .ge(UserTaskRecordEntity::getCompleteTime, windowStart)
                    .orderByDesc(UserTaskRecordEntity::getCompleteTime)
                    .last("LIMIT 1")); // 返回一条最近的记录
        }

        throw new IllegalArgumentException("Unsupported task limit: " + limit.getType());
    }

    private void grantReward(Long userId, RewardType rewardType, int rewardAmount,
                             String walletTraceId, Long operatorId, String meta) {
        if (RewardType.NONE.equals(rewardType)) {
            return; // 不发奖励
        }
        if (RewardType.TOKEN.equals(rewardType)) {
            walletService.changeUserTokenBalance(
                    userId, operatorId, walletTraceId, rewardAmount, WalletTransactionType.GIFT, meta);
            return;
        }
        if (RewardType.COIN.equals(rewardType)) {
            walletService.changeCoinBalance(
                    userId, operatorId, walletTraceId, rewardAmount, WalletTransactionType.GIFT, meta);
            return;
        }
        throw new IllegalArgumentException("unsupported reward type: " + rewardType);
    }

    private void publishRewardMessage(Long userId, UserTaskCode taskCode, UserTaskHandler.UserTaskContext context,
                                      UserTaskHandler handler, UserTaskRecordEntity record,
                                      UserTaskHandler.UserTaskReward reward) {
        if (RewardType.NONE.equals(record.getRewardType()) || record.getRewardAmount() == null
                || record.getRewardAmount() <= 0) {
            return;
        }

        MessagePublishRequest message = handler.buildRewardMessage(userId, taskCode, context, record, reward);
        if (message == null) {
            return;
        }

        message.setReceiverUserIds(List.of(userId));
        message.setDeliveryScope(MessageDeliveryScope.DIRECT);
        message.setMessageType(MessageType.SYSTEM);
        message.setSourceService(BusinessDomain.USER);
        message.setBizTraceId("USER_TASK_REWARD:" + record.getId());
        messageService.publishMessage(message);
    }
}
