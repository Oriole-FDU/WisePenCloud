package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.domain.base.TagInfoPermissionBase;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class TagTreeResponse extends TagInfoPermissionBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "标签 ID")
    private String tagId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "父标签 ID；根节点为 0")
    private String parentId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "创建者展示信息")
    private UserDisplayBase creatorInfo;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "当前用户在该标签上可执行的资源动作")
    private List<ResourceAction> grantedActions = new ArrayList<>();

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "子标签列表")
    private List<TagTreeResponse> children = new ArrayList<>(); // 子节点列表，用于在内存中组装树返回给前端
}
