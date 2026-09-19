package com.oriole.wisepen.user.task;

import com.oriole.wisepen.user.api.config.UserTaskProperties;
import com.oriole.wisepen.user.api.domain.dto.req.MessagePublishRequest;
import com.oriole.wisepen.user.api.domain.dto.res.UserTaskStatusResponse;
import com.oriole.wisepen.user.api.enums.RewardType;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import com.oriole.wisepen.user.domain.entity.UserTaskRecordEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class InviteVerifiedUserTaskHandler implements UserTaskHandler {

    private static final String META = "邀请用户认证赠送";

    private final UserTaskProperties userTaskProperties;

    @Override
    public Set<UserTaskCode> getTaskCodes() {
        return Set.of(UserTaskCode.INVITE_VERIFIED_USER);
    }

    @Override
    public boolean isEnabled(UserTaskCode taskCode) {
        return Boolean.TRUE.equals(userTaskProperties.getInviteVerifiedUser().getEnabled());
    }

    @Override
    public UserTaskLimit getLimit(UserTaskCode taskCode) {
        return UserTaskLimit.builder().type(UserTaskLimit.LimitType.UNCHECKED).build();
    }

    @Override
    public UserTaskStatusResponse.UserTaskRewardPreview previewReward(UserTaskCode taskCode) {
        UserTaskProperties.InviteVerifiedUser config = userTaskProperties.getInviteVerifiedUser();
        int minAmount = positiveOrDefault(config.getMinRewardAmount(), 1000000);
        int maxAmount = Math.max(positiveOrDefault(config.getMaxRewardAmount(), 10000000), minAmount);
        return UserTaskStatusResponse.UserTaskRewardPreview.builder()
                .rewardType(config.getRewardType() == null ? RewardType.TOKEN : config.getRewardType())
                .minRewardAmount(minAmount)
                .maxRewardAmount(maxAmount)
                .rewardStepAmount(positiveOrDefault(config.getRewardStepAmount(), 1000000))
                .build();
    }

    @Override
    // 计算邀请认证奖励
    public UserTaskReward calculateReward(Long userId, UserTaskCode taskCode, UserTaskContext context) {
        UserTaskProperties.InviteVerifiedUser config = userTaskProperties.getInviteVerifiedUser();
        int minAmount = positiveOrDefault(config.getMinRewardAmount(), 1000000);
        int maxAmount = Math.max(positiveOrDefault(config.getMaxRewardAmount(), 10000000), minAmount);
        int rewardStepAmount = positiveOrDefault(config.getRewardStepAmount(), 1000000);
        int minUnit = (minAmount + rewardStepAmount - 1) / rewardStepAmount;
        int maxUnit = maxAmount / rewardStepAmount;
        // 按奖励步长生成随机奖励
        int rewardAmount = maxUnit < minUnit
                ? minAmount
                : ThreadLocalRandom.current().nextInt(minUnit, maxUnit + 1) * rewardStepAmount;
        return UserTaskReward.builder()
                .rewardType(config.getRewardType() == null ? RewardType.TOKEN : config.getRewardType())
                .rewardAmount(rewardAmount)
                .build();
    }

    @Override
    public String buildMeta(Long userId, UserTaskCode taskCode, UserTaskContext context, UserTaskReward reward) {
        String meta = context == null ? null : context.getMeta();
        return meta == null || meta.isBlank() ? META : meta;
    }

    @Override
    public MessagePublishRequest buildRewardMessage(Long userId, UserTaskCode taskCode,
                                                     UserTaskContext context, UserTaskRecordEntity record,
                                                     UserTaskReward reward) {
        Long inviteeUserId = context == null ? null : context.getOperatorId();
        String rewardUnit = RewardType.COIN.equals(record.getRewardType()) ? "Coin" : "Token";
        String inviteeText = inviteeUserId == null ? "你邀请的用户" : "{{USER:" + inviteeUserId + "}}";
        return MessagePublishRequest.builder()
                .title("邀请奖励到账")
                .content(String.format("%s已完成认证，你获得 %,d %s。", inviteeText, record.getRewardAmount(), rewardUnit))
                .build();
    }

    @Override
    public Object buildCompletedResponse(Long userId, UserTaskCode taskCode, UserTaskContext context,
                                         UserTaskRecordEntity record, UserTaskReward reward) {
        return Boolean.TRUE;
    }

    @Override
    public Object buildSkippedResponse(Long userId, UserTaskCode taskCode, UserTaskContext context,
                                       UserTaskRecordEntity existingRecord) {
        return Boolean.FALSE;
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }
}
