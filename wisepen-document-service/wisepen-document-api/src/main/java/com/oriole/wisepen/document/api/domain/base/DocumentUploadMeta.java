package com.oriole.wisepen.document.api.domain.base;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadMeta {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "文档名称")
    private String documentName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "上传者用户 ID")
    private Long uploaderId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "上传者在各小组中的角色；仅首次上传时有值，无角色时为空 Map")
    @Builder.Default
    private Map<Long, GroupRoleType> uploaderGroupRoles = new HashMap<>();

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "文件类型")
    private ResourceType fileType;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "文件大小")
    private Long size;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "挂载目标标签 ID；仅首次上传时有值")
    private String mountTargetTagId;
}
