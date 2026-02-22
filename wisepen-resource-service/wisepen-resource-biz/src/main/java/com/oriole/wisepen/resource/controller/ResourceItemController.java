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
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/resource/item")
@RequiredArgsConstructor
@CheckLogin
public class ResourceItemController {

    private final IResourceService resourceService;


    // 重命名资源
    @PostMapping("/renameRes")
    public R<Void> renameResource(@Validated @RequestBody ResourceRenameRequest req) {
        resourceService.assertResourceOwner(req.getResourceId(), SecurityContextHolder.getUserId());
        resourceService.renameResource(req);
        return R.ok();
    }

    // 编辑资源的所属标签
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
    // 个人All (!groupId && !tagId)：查 ownerId = userId 的所有资源；
    // 小组All (groupId && !tagId)：查挂载在该小组下，且有权限看到的资源；
    // 个人指定Tag (!groupId && tagId)：查 ownerId = userId，且带有此标签的资源；
    // 小组指定Tag (groupId && tagId)：查挂载在该小组该标签下，且有权限看到的资源。

    // 特别说明：个人不传groupId, 也不传虚拟的个人groupId（即p_开头的）
    @GetMapping("/list")
    public R<PageResult<ResourceItemResponse>> listResources(
            @RequestParam(value = "groupId", required = false) String groupId,
            @RequestParam(value = "tagIds", required = false) List<String> tagIds,
            @RequestParam(value = "tagQueryLogicMode", defaultValue = "OR") QueryLogicEnum tagQueryLogicMode,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
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