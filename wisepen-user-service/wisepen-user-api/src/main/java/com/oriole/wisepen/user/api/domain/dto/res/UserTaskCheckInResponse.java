package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.enums.RewardType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserTaskCheckInResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "奖励类型")
    private RewardType rewardType;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "奖励数量")
    private Integer rewardAmount;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否触发保底奖励")
    private Boolean hitGuarantee;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "当前周期进度")
    private Integer cycleProgress;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "周期总次数")
    private Integer cycleCount;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "今日是否已签到")
    private Boolean checkedInToday;
}
