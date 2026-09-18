package com.oriole.wisepen.user.task;

import com.oriole.wisepen.user.api.config.UserTaskProperties;
import com.oriole.wisepen.user.api.domain.dto.res.UserTaskStatusResponse;
import com.oriole.wisepen.user.api.enums.RewardType;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import com.oriole.wisepen.user.domain.entity.UserTaskRecordEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class VerificationTaskHandler implements UserTaskHandler {

    private final UserTaskProperties userTaskProperties;

    @Override
    public Set<UserTaskCode> getTaskCodes() {
        return Set.of(UserTaskCode.STUDENT_VERIFICATION, UserTaskCode.TEACHER_VERIFICATION);
    }

    @Override
    public boolean isEnabled(UserTaskCode taskCode) {
        UserTaskProperties.Rule rule = userTaskProperties.getRules().get(taskCode.getValue());
        return rule != null && Boolean.TRUE.equals(rule.getEnabled());
    }

    @Override
    public UserTaskLimit getLimit(UserTaskCode taskCode) {
        return UserTaskLimit.once();
    }

    @Override
    public UserTaskStatusResponse.UserTaskRewardPreview previewReward(UserTaskCode taskCode) {
        UserTaskProperties.Rule rule = userTaskProperties.getRules().get(taskCode.getValue());
        if (rule == null) {
            return UserTaskStatusResponse.UserTaskRewardPreview.builder().rewardType(RewardType.NONE).rewardAmount(0).build();
        }
        RewardType rewardType = rule.getRewardType() == null ? RewardType.NONE : rule.getRewardType();
        int rewardAmount = rule.getRewardAmount() == null ? 0 : rule.getRewardAmount();
        return UserTaskStatusResponse.UserTaskRewardPreview.builder()
                .rewardType(rewardType)
                .rewardAmount(Math.max(rewardAmount, 0))
                .build();
    }

    @Override
    public UserTaskReward calculateReward(Long userId, UserTaskCode taskCode, UserTaskContext context) {
        UserTaskProperties.Rule rule = userTaskProperties.getRules().get(taskCode.getValue());
        if (rule == null) return null;

        RewardType rewardType = rule.getRewardType() == null ? RewardType.NONE : rule.getRewardType();
        int rewardAmount = rule.getRewardAmount() == null ? 0 : rule.getRewardAmount();
        if (!RewardType.NONE.equals(rewardType) && rewardAmount <= 0) return null;

        return UserTaskReward.builder().rewardType(rewardType).rewardAmount(Math.max(rewardAmount, 0)).build();
    }

    @Override
    public String buildMeta(Long userId, UserTaskCode taskCode, UserTaskContext context, UserTaskReward reward) {
        return context == null ? null : context.getMeta();
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
}
