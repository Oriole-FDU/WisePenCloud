package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.resource.enums.ResourceAccessRole;
import com.oriole.wisepen.resource.enums.ResourceAction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
public class ResourceCheckPermissionResDTO {

    // 用户是以什么身份/角色访问这个资源的
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "用户访问资源的身份")
    private ResourceAccessRole resourceAccessRole;

    // 这个身份是哪里赋予的
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "权限来源")
    private Set<String> permissionSources;

    // 可用动作有哪些
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "可用动作")
    private List<ResourceAction> allowedActions;

    public ResourceCheckPermissionResDTO(ResourceAccessRole resourceAccessRole) {
        this.resourceAccessRole = resourceAccessRole;
        this.permissionSources = Set.of();
        this.allowedActions = List.of();
    }
}
