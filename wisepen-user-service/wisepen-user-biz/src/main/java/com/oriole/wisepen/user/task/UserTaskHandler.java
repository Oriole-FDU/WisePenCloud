package com.oriole.wisepen.user.task;

import com.oriole.wisepen.user.api.domain.dto.res.UserTaskStatusResponse;
import com.oriole.wisepen.user.api.enums.RewardType;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import com.oriole.wisepen.user.domain.entity.UserTaskRecordEntity;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

public interface UserTaskHandler {

    Set<UserTaskCode> getTaskCodes();

    boolean isEnabled(UserTaskCode taskCode);

    UserTaskLimit getLimit(UserTaskCode taskCode);

    UserTaskStatusResponse.UserTaskRewardPreview previewReward(UserTaskCode taskCode);

    UserTaskReward calculateReward(Long userId, UserTaskCode taskCode, UserTaskContext context);

    String buildMeta(Long userId, UserTaskCode taskCode, UserTaskContext context, UserTaskReward reward);

    Object buildCompletedResponse(Long userId, UserTaskCode taskCode, UserTaskContext context,
                                  UserTaskRecordEntity record, UserTaskReward reward);

    Object buildSkippedResponse(Long userId, UserTaskCode taskCode, UserTaskContext context, UserTaskRecordEntity existingRecord);

    @Data
    @Builder
    class UserTaskContext {
        private Long operatorId;
        private String meta;
    }

    @Data
    @Builder
    class UserTaskLimit {

        private LimitType type;

        private Integer maxTimes;

        private Integer refreshCycleDays;

        public static UserTaskLimit once() {
            return UserTaskLimit.builder().type(LimitType.ONCE).maxTimes(1).build();
        }

        public static UserTaskLimit dailyMaxTimes(Integer refreshCycleDays, Integer maxTimes) {
            return UserTaskLimit.builder()
                    .type(LimitType.DAILY_MAX_TIMES)
                    .refreshCycleDays(refreshCycleDays)
                    .maxTimes(maxTimes)
                    .build();
        }

        public enum LimitType {
            ONCE,
            DAILY_MAX_TIMES,
            UNCHECKED
        }
    }

    @Data
    @Builder
    class UserTaskReward {
        private RewardType rewardType;
        private Integer rewardAmount;
        private Boolean hitGuarantee;
        private Integer cycleProgress;
        private Integer cycleCount;
    }
}
