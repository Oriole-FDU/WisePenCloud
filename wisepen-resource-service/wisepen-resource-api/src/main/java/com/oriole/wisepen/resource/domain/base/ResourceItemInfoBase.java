package com.oriole.wisepen.resource.domain.base;

import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceItemInfoBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源名称")
    private String resourceName;      // 资源名称/标题

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源类型")
    private ResourceType resourceType;// 资源类型

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "所有者用户 ID")
    private String ownerId;           // 所有者

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "预览图 URL")
    private String preview;           // 可选：预览图

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "string", format = "int64", description = "文件大小或字数摘要")
    private Long size;                // 可选：文件大小/字数摘要

    /** 阅读量、点赞、评分、收藏等统计数据 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源互动统计")
    @Builder.Default
    private ResourceInteractionInfoBase resourceInteractionInfo = new ResourceInteractionInfoBase();
}
