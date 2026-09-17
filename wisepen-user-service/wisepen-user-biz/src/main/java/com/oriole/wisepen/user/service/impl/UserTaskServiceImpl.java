package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.user.api.enums.RewardType;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import com.oriole.wisepen.user.api.enums.WalletTransactionType;
import com.oriole.wisepen.user.domain.entity.UserTaskRecordEntity;
import com.oriole.wisepen.user.mapper.UserTaskRecordMapper;
import com.oriole.wisepen.user.service.IUserTaskService;
import com.oriole.wisepen.user.service.IWalletService;
import com.oriole.wisepen.user.task.UserTaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class UserTaskServiceImpl implements IUserTaskService {

    private final UserTaskRecordMapper userTaskRecordMapper;
    private final IWalletService walletService;
    private final Map<UserTaskCode, UserTaskHandler> handlerMap = new EnumMap<>(UserTaskCode.class);

    // Spring 会注入所有 UserTaskHandler
    public UserTaskServiceImpl(UserTaskRecordMapper userTaskRecordMapper, IWalletService walletService, List<UserTaskHandler> handlers) {
        this.userTaskRecordMapper = userTaskRecordMapper;
        this.walletService = walletService;
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
        UserTaskRecordEntity existingRecord = findExistingRecord(userId, taskCode, limit);
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
        return handler.buildCompletedResponse(userId, taskCode, context, record, reward);
    }

    private UserTaskRecordEntity findExistingRecord(Long userId, UserTaskCode taskCode, UserTaskHandler.UserTaskLimit limit) {
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
}
