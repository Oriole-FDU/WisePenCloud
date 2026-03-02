package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Map;

@Data
public class ResourceCheckPermissionDTO {
    @NotBlank(message = ResourceValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;
    @NotBlank(message = ResourceValidationMsg.RESOURCE_TYPE_NOT_BLANK)
    private String resourceType;
    @NotBlank(message = ResourceValidationMsg.USER_ID_NOT_BLANK)
    private String userId;
    // 用户所属的群组及对应角色
    @NotNull(message = ResourceValidationMsg.USER_GROUP_ROLES_NOT_NULL)
    private Map<String, GroupRoleType> groupRoles;
}