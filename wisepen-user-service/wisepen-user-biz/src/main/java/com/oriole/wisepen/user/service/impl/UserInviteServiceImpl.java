package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.res.UserInviteRecordResponse;
import com.oriole.wisepen.user.api.domain.mq.UserTaskCompleteMessage;
import com.oriole.wisepen.user.api.enums.UserInviteStatus;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import com.oriole.wisepen.user.domain.entity.UserInviteRecordEntity;
import com.oriole.wisepen.user.domain.entity.UserProfileEntity;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.mapper.UserInviteRecordMapper;
import com.oriole.wisepen.user.mapper.UserProfileMapper;
import com.oriole.wisepen.user.mq.KafkaUserEventPublisher;
import com.oriole.wisepen.user.service.IDisplayService;
import com.oriole.wisepen.user.service.IUserInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserInviteServiceImpl implements IUserInviteService {

    private static final int INVITE_CODE_LENGTH = 8;

    private final UserProfileMapper userProfileMapper;
    private final UserInviteRecordMapper userInviteRecordMapper;
    private final KafkaUserEventPublisher kafkaUserEventPublisher;
    private final IDisplayService displayService;

    @Override
    // 生成唯一邀请码
    public String generateInviteCode() {
        String inviteCode;
        do {
            inviteCode = IdUtil.fastSimpleUUID()
                    .substring(0, INVITE_CODE_LENGTH)
                    .toUpperCase(Locale.ROOT);
        } while (userProfileMapper.selectCount(Wrappers.<UserProfileEntity>lambdaQuery()
                .eq(UserProfileEntity::getInviteCode, inviteCode)) > 0);
        return inviteCode;
    }

    @Override
    // 注册时绑定邀请关系
    public void bindAtRegistration(Long inviteeUserId, String inviteCode) {
        if (StrUtil.isBlank(inviteCode)) {
            return;
        }

        String normalizedInviteCode = inviteCode.trim().toUpperCase(Locale.ROOT);
        UserProfileEntity inviterProfile = userProfileMapper.selectOne(
                Wrappers.<UserProfileEntity>lambdaQuery()
                        .eq(UserProfileEntity::getInviteCode, normalizedInviteCode)
                        .last("LIMIT 1"));
        if (inviterProfile == null) {
            throw new ServiceException(UserError.INVITE_CODE_NOT_FOUND);
        }
        if (inviterProfile.getUserId().equals(inviteeUserId)) {
            throw new ServiceException(UserError.INVITE_SELF_NOT_ALLOWED);
        }
        userInviteRecordMapper.insert(UserInviteRecordEntity.builder()
                .inviterUserId(inviterProfile.getUserId())
                .inviteeUserId(inviteeUserId)
                .status(UserInviteStatus.BOUND)
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 认证成功后兑现邀请奖励
    public void rewardInviterAfterVerification(Long inviteeUserId) {
        UserInviteRecordEntity inviteRecord = userInviteRecordMapper.selectOne(
                Wrappers.<UserInviteRecordEntity>lambdaQuery()
                        .eq(UserInviteRecordEntity::getInviteeUserId, inviteeUserId)
                        .eq(UserInviteRecordEntity::getStatus, UserInviteStatus.BOUND)
                        .last("LIMIT 1"));
        if (inviteRecord == null) {
            return;
        }

        // 先占用邀请记录，避免同一邀请关系重复触发任务
        int affectedRows = userInviteRecordMapper.update(
                null,
                new LambdaUpdateWrapper<UserInviteRecordEntity>()
                        .eq(UserInviteRecordEntity::getId, inviteRecord.getId())
                        .eq(UserInviteRecordEntity::getStatus, UserInviteStatus.BOUND)
                        .set(UserInviteRecordEntity::getStatus, UserInviteStatus.REWARDED)
                        .set(UserInviteRecordEntity::getRewardTime, LocalDateTime.now())
        );
        if (affectedRows == 0) {
            return;
        }

        // 奖励金额和钱包流水由任务系统异步处理
        kafkaUserEventPublisher.publishUserTaskComplete(UserTaskCompleteMessage.builder()
                .userId(inviteRecord.getInviterUserId())
                .taskCode(UserTaskCode.INVITE_VERIFIED_USER)
                .operatorId(inviteeUserId)
                .meta("邀请用户认证赠送")
                .build());
    }

    @Override
    // 分页查询用户发出的邀请记录
    public PageR<UserInviteRecordResponse> listInviteRecords(Long inviterUserId, Integer page, Integer size) {
        IPage<UserInviteRecordEntity> recordPage = userInviteRecordMapper.selectPage(
                new Page<>(page, size),
                Wrappers.<UserInviteRecordEntity>lambdaQuery()
                        .eq(UserInviteRecordEntity::getInviterUserId, inviterUserId)
                        .orderByDesc(UserInviteRecordEntity::getCreateTime)
        );
        PageR<UserInviteRecordResponse> pageR = new PageR<>(recordPage.getTotal(), page, size);
        if (recordPage.getRecords().isEmpty()) {
            return pageR;
        }

        Set<Long> inviteeUserIds = recordPage.getRecords().stream()
                .map(UserInviteRecordEntity::getInviteeUserId)
                .collect(Collectors.toSet());
        Map<Long, UserDisplayBase> inviteeDisplayMap = inviteeUserIds.isEmpty()
                ? Collections.emptyMap()
                : displayService.getUserDisplayInfoByIds(inviteeUserIds);

        pageR.addAll(recordPage.getRecords().stream()
                .map(record -> UserInviteRecordResponse.builder()
                        .id(record.getId())
                        .inviteeUserId(record.getInviteeUserId())
                        .inviteeDisplay(inviteeDisplayMap.get(record.getInviteeUserId()))
                        .status(record.getStatus())
                        .createTime(record.getCreateTime())
                        .rewardTime(record.getRewardTime())
                        .build())
                .collect(Collectors.toList()));
        return pageR;
    }
}
