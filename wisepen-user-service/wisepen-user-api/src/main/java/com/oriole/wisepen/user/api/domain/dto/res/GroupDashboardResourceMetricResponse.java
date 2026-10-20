package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.GroupResourceMetricBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class GroupDashboardResourceMetricResponse extends GroupResourceMetricBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源 ID")
    private String resourceId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源名称")
    private String resourceName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源类型")
    private String resourceType;
}
