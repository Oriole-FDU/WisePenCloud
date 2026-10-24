package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.resource.enums.ResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceItemInfoResDTO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源 ID")
    private String resourceId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源名称")
    private String resourceName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源类型")
    private ResourceType resourceType;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "所有者用户 ID")
    private String ownerId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "预览图 URL")
    private String preview;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "string", format = "int64", description = "文件大小或字数摘要")
    private Long size;
}
