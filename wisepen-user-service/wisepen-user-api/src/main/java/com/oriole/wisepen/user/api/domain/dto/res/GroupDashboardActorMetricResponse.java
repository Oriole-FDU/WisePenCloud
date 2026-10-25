package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.GroupResourceMetricBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class GroupDashboardActorMetricResponse extends GroupResourceMetricBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "行为用户 ID")
    private Long actorUserId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "行为用户展示信息")
    private UserDisplayBase actorInfo;
}
