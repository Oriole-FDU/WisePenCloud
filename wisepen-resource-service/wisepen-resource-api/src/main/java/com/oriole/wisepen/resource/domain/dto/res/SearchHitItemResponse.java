package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHitItemResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源 ID")
    private String resourceId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源类型")
    private ResourceType resourceType;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源名称")
    private String resourceName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "搜索高亮内容")
    private String highlightContent;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "更新时间")
    private LocalDateTime updateTime;
}
