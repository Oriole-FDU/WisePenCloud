package com.oriole.wisepen.resource.controller;

import com.alibaba.nacos.common.utils.StringUtils;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.list.QueryLogicEnum;
import com.oriole.wisepen.common.core.domain.enums.list.SortDirectionEnum;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.resource.constant.ResourceConstants;
import com.oriole.wisepen.resource.domain.dto.ResourceItemResponse;
import com.oriole.wisepen.resource.domain.dto.ResourceRenameRequest;
import com.oriole.wisepen.resource.domain.dto.ResourceUpdateTagsRequest;
import com.oriole.wisepen.resource.enums.ResourceSortByEnum;
import com.oriole.wisepen.resource.service.IResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "资源管理", description = "资源重命名、更新资源标签与列出资源")
@RestController
@RequestMapping("/resource/item")
@RequiredArgsConstructor
@CheckLogin
public class ResourceItemController {

    private final IResourceService resourceService;

    // 重命名资源
    @Operation(summary = "重命名资源", description = "用户修改资源名称")
    @PostMapping("/renameRes")
    public R<Void> renameResource(@Validated @RequestBody ResourceRenameRequest req) {
        resourceService.assertResourceOwner(req.getResourceId(), SecurityContextHolder.getUserId());
        resourceService.renameResource(req);
        return R.ok();
    }

    // 编辑资源的所属标签
    @Operation(summary = "更新资源标签", description = "用户修改资源的标签列表")
    @PostMapping("/updateTags")
    public R<Void> updateResourceTags(@Validated @RequestBody ResourceUpdateTagsRequest req) {
        String userId = SecurityContextHolder.getUserId();
        resourceService.assertResourceOwner(req.getResourceId(), userId);
        if (!StringUtils.hasText(req.getGroupId())) {
            req.setGroupId(ResourceConstants.PERSONAL_GROUP_PREFIX + userId);
        } else {
            // 如果传了 groupId，则必须校验用户在该组内
            SecurityContextHolder.assertInGroup(req.getGroupId());
        }
        resourceService.updateResourceTags(req);
        return R.ok();
    }

    // 列出资源
    @Operation(summary = "分页列出资源", description = "多条件组合的高级分页查询，支持按字段升降序，多标签与/或筛选。<br><br>" +
            "**核心查询场景说明：**<br>" +
            "1. **个人所有资源** (`不传 groupId` 且 `不传 tagIds`)：查询当前用户作为 owner 的所有资源。<br>" +
            "2. **小组所有资源** (`传 groupId` 且 `不传 tagIds`)：查询挂载在该小组下，且当前用户有权限看到的资源。<br>" +
            "3. **个人指定标签** (`不传 groupId` 且 `传 tagIds`)：查询当前用户拥有，且带有指定标签的资源。<br>" +
            "4. **小组指定标签** (`传 groupId` 且 `传 tagIds`)：查询挂载在该小组下、具有指定标签，且当前用户有权限看到的资源。")
    @GetMapping("/list")
    public R<PageResult<ResourceItemResponse>> listResources(
            @Parameter(description = "小组ID。查个人资源时必须留空")
            @RequestParam(value = "groupId", required = false) String groupId,
            @Parameter(description = "标签ID列表，为空时列出全部资源。配合 groupId 决定查个人的标签还是小组的标签。")
            @RequestParam(value = "tagIds", required = false) List<String> tagIds,
            @Parameter(description = "多标签组合查询时的逻辑关系(AND/OR)")
            @RequestParam(value = "tagQueryLogicMode", defaultValue = "OR") QueryLogicEnum tagQueryLogicMode,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "排序字段枚举")
            @RequestParam(value = "sortBy", defaultValue = "UPDATE_TIME") ResourceSortByEnum sortBy,
            @RequestParam(value = "sortDir", defaultValue = "DESC") SortDirectionEnum sortDir) {
        String userId = SecurityContextHolder.getUserId();

        GroupRoleType userGroupRole = null;
        if (StringUtils.hasText(groupId)) {
            userGroupRole = SecurityContextHolder.assertInGroup(groupId); // 不传groupId无需检查小组权限
        }

        PageResult<ResourceItemResponse> result = resourceService.listResources(
                userId,
                groupId,
                userGroupRole,
                tagIds,
                tagQueryLogicMode,
                resourceType,
                page,
                size,
                sortBy,
                sortDir
        );
        return R.ok(result);
    }
}