package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.enums.RewardType;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import com.oriole.wisepen.user.api.enums.UserTaskType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserTaskStatusResponse {
    private UserTaskCode taskCode;
    private UserTaskType taskType;
    private Boolean enabled;
    private Boolean canComplete;
    private UserTaskRewardPreview rewardPreview;
    private OnceTaskStatus once;
    private PeriodicTaskStatus periodic;

    @Data
    @Builder
    public static class UserTaskRewardPreview {
        private RewardType rewardType;
        private Integer rewardAmount;
        private Integer minRewardAmount;
        private Integer maxRewardAmount;
        private Integer rewardStepAmount;
    }

    @Data
    @Builder
    public static class OnceTaskStatus {
        private Boolean completed;
        private LocalDateTime completeTime;
    }

    @Data
    @Builder
    public static class PeriodicTaskStatus {
        private Integer completedTimes;
        private Integer maxTimes;
        private LocalDateTime windowStart;
        private LocalDateTime windowEnd;
        private LocalDateTime lastCompleteTime;
    }
}
