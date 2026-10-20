package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.enums.RewardType;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import com.oriole.wisepen.user.api.enums.UserTaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserTaskStatusResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "任务编码")
    private UserTaskCode taskCode;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "任务类型")
    private UserTaskType taskType;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否启用")
    private Boolean enabled;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否可完成")
    private Boolean canComplete;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "奖励预览")
    private UserTaskRewardPreview rewardPreview;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "一次性任务状态")
    private OnceTaskStatus once;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "周期任务状态")
    private PeriodicTaskStatus periodic;

    @Data
    @Builder
    public static class UserTaskRewardPreview {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "奖励类型")
        private RewardType rewardType;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "固定奖励数量；随机奖励任务为空")
        private Integer rewardAmount;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "最小奖励数量")
        private Integer minRewardAmount;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "最大奖励数量")
        private Integer maxRewardAmount;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "奖励步进值")
        private Integer rewardStepAmount;
    }

    @Data
    @Builder
    public static class OnceTaskStatus {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否已完成")
        private Boolean completed;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "完成时间")
        private LocalDateTime completeTime;
    }

    @Data
    @Builder
    public static class PeriodicTaskStatus {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "已完成次数")
        private Integer completedTimes;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "最大次数")
        private Integer maxTimes;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "周期开始时间")
        private LocalDateTime windowStart;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "周期结束时间")
        private LocalDateTime windowEnd;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "最近完成时间")
        private LocalDateTime lastCompleteTime;
    }
}
