package com.oriole.wisepen.user.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.user.api.config.UserTaskProperties;
import com.oriole.wisepen.user.api.domain.dto.res.UserTaskCheckInResponse;
import com.oriole.wisepen.user.api.enums.RewardType;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import com.oriole.wisepen.user.domain.entity.UserTaskRecordEntity;
import com.oriole.wisepen.user.mapper.UserTaskRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class DailyCheckInTaskHandler implements UserTaskHandler {

    private static final String META = "每日签到赠送";

    private final UserTaskProperties userTaskProperties;
    private final UserTaskRecordMapper userTaskRecordMapper;

    @Override
    public Set<UserTaskCode> getTaskCodes() {
        return Set.of(UserTaskCode.DAILY_CHECK_IN);
    }

    @Override
    public boolean isEnabled(UserTaskCode taskCode) {
        return Boolean.TRUE.equals(userTaskProperties.getDailyCheckIn().getEnabled());
    }

    @Override
    public UserTaskLimit getLimit(UserTaskCode taskCode) {
        UserTaskProperties.DailyCheckIn config = userTaskProperties.getDailyCheckIn();
        // 在 refreshCycleDays 天窗口内最多 1 次
        return UserTaskLimit.dailyMaxTimes(positiveOrDefault(config.getRefreshCycleDays(), 1), 1);
    }

    @Override
    public UserTaskReward calculateReward(Long userId, UserTaskCode taskCode, UserTaskContext context) {
        UserTaskProperties.DailyCheckIn config = userTaskProperties.getDailyCheckIn();
        RewardType rewardType = config.getRewardType() == null ? RewardType.TOKEN : config.getRewardType(); // 奖励类型，默认 TOKEN
        int cycleCount = positiveOrDefault(config.getGuaranteeCycleCount(), 10); // 保底周期，默认 10 次
        int minAmount = positiveOrDefault(config.getMinRewardAmount(), 100000); // 最低奖励，默认 0.1M
        int maxAmount = Math.max(positiveOrDefault(config.getMaxRewardAmount(), 1000000), minAmount); // 最高奖励，默认 1M
        int rewardStepAmount = positiveOrDefault(config.getRewardStepAmount(), 100000); // 励步长，默认 0.1M

        // 计算当前周期进度
        int cycleProgress = calculateCycleProgress(userId, maxAmount, cycleCount, null);
        boolean hitGuarantee = cycleProgress >= cycleCount - 1; // 判断这次是否触发保底
        // 如果触发保底，直接给 maxAmount，否则随机给一个阶梯金额
        int rewardAmount = hitGuarantee ? maxAmount : randomSteppedAmount(minAmount, maxAmount, rewardStepAmount);
        return UserTaskReward.builder()
                .rewardType(rewardType)
                .rewardAmount(rewardAmount)
                .hitGuarantee(hitGuarantee)
                .cycleProgress(rewardAmount >= maxAmount ? cycleCount : cycleProgress + 1)
                .cycleCount(cycleCount)
                .build();
    }

    @Override
    public String buildMeta(Long userId, UserTaskCode taskCode, UserTaskContext context, UserTaskReward reward) {
        // 生成任务记录和钱包流水的 meta
        String meta = context == null ? null : context.getMeta();
        if (meta == null || meta.isBlank()) {
            meta = META;
        }
        return Boolean.TRUE.equals(reward.getHitGuarantee()) ? meta + "（签到保底）" : meta;
    }

    @Override
    public Object buildCompletedResponse(Long userId, UserTaskCode taskCode, UserTaskContext context,
                                         UserTaskRecordEntity record, UserTaskReward reward) {
        return UserTaskCheckInResponse.builder()
                .rewardType(record.getRewardType())
                .rewardAmount(record.getRewardAmount())
                .hitGuarantee(reward.getHitGuarantee())
                .cycleProgress(reward.getCycleProgress())
                .cycleCount(reward.getCycleCount())
                .checkedInToday(true)
                .build();
    }

    @Override
    public Object buildSkippedResponse(Long userId, UserTaskCode taskCode, UserTaskContext context,
                                       UserTaskRecordEntity existingRecord) {
        UserTaskProperties.DailyCheckIn config = userTaskProperties.getDailyCheckIn();
        RewardType rewardType = config.getRewardType() == null ? RewardType.TOKEN : config.getRewardType();
        if (existingRecord == null) { // 签到关闭
            return UserTaskCheckInResponse.builder()
                    .rewardType(rewardType)
                    .rewardAmount(0)
                    .hitGuarantee(false)
                    .cycleProgress(0)
                    .cycleCount(positiveOrDefault(config.getGuaranteeCycleCount(), 10))
                    .checkedInToday(false)
                    .build();
        }

        int cycleCount = positiveOrDefault(config.getGuaranteeCycleCount(), 10);
        int minAmount = positiveOrDefault(config.getMinRewardAmount(), 100000);
        int maxAmount = Math.max(positiveOrDefault(config.getMaxRewardAmount(), 1000000), minAmount);

        int cycleProgress = existingRecord.getRewardAmount() != null && existingRecord.getRewardAmount() >= maxAmount
                ? cycleCount : calculateCycleProgress(userId, maxAmount, cycleCount, existingRecord.getCompleteTime());

        return UserTaskCheckInResponse.builder()
                .rewardType(existingRecord.getRewardType())
                .rewardAmount(existingRecord.getRewardAmount())
                .hitGuarantee(false)
                .cycleProgress(cycleProgress)
                .cycleCount(cycleCount)
                .checkedInToday(true)
                .build();
    }

    private int calculateCycleProgress(Long userId, int maxAmount, int cycleCount, LocalDateTime endTime) {
        // 查询上一次最高奖记录
        UserTaskRecordEntity lastMaxRewardRecord = userTaskRecordMapper.selectOne(Wrappers.<UserTaskRecordEntity>lambdaQuery()
                .eq(UserTaskRecordEntity::getUserId, userId)
                .eq(UserTaskRecordEntity::getTaskCode, UserTaskCode.DAILY_CHECK_IN)
                .eq(UserTaskRecordEntity::getRewardAmount, maxAmount)
                .lt(endTime != null, UserTaskRecordEntity::getCompleteTime, endTime)
                .orderByDesc(UserTaskRecordEntity::getCompleteTime)
                .last("LIMIT 1"));

        // 统计最近一次最高奖之后，用户已经签到多少次
        Long completedCount = userTaskRecordMapper.selectCount(Wrappers.<UserTaskRecordEntity>lambdaQuery()
                .eq(UserTaskRecordEntity::getUserId, userId)
                .eq(UserTaskRecordEntity::getTaskCode, UserTaskCode.DAILY_CHECK_IN)
                .gt(lastMaxRewardRecord != null, UserTaskRecordEntity::getCompleteTime,
                        lastMaxRewardRecord == null ? null : lastMaxRewardRecord.getCompleteTime())
                .le(endTime != null, UserTaskRecordEntity::getCompleteTime, endTime));

        return Math.min(completedCount.intValue(), cycleCount);
    }

    private int randomSteppedAmount(int minAmount, int maxAmount, int rewardStepAmount) {
        int minUnit = (minAmount + rewardStepAmount - 1) / rewardStepAmount;
        int maxUnit = maxAmount / rewardStepAmount;
        if (maxUnit < minUnit) {
            return minAmount;
        }
        return ThreadLocalRandom.current().nextInt(minUnit, maxUnit + 1) * rewardStepAmount;
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }
}
