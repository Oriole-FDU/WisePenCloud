package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.resource.constant.ResourceConstants;
import com.oriole.wisepen.resource.domain.dto.req.ResourcePlacementGroupMountRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourcePlacementGroupMoveRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourcePlacementGroupUnmountRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourcePlacementPersonalNormalTagsRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourcePlacementPersonalPathTagRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourcePlacementPersonalTrashRequest;
import com.oriole.wisepen.resource.domain.dto.res.ResourcePlacementResponse;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.service.IResourcePlacementService;
import com.oriole.wisepen.resource.service.IResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

@Tag(name = "资源挂载", description = "个人与小组资源挂载位置维护")
@RestController
@RequestMapping("/resource/placement")
@RequiredArgsConstructor
@CheckRole
@Validated
public class ResourcePlacementController {

    private final IResourcePlacementService resourcePlacementService;
    private final IResourceService resourceService;

    @Operation(
            summary = "移动个人资源路径标签",
            description = """
                    - 用途：将个人空间中的资源移动到指定路径标签下。
                    - 请求：resourceIds 指定目标资源列表；targetPathTagId 指定新的个人路径标签。
                    - 约束：当前用户必须是每个目标资源的所有者；目标标签必须属于当前用户个人空间且必须是路径标签。
                    - 处理：只替换资源在个人空间绑定列表中的首位路径标签，保留普通标签；若目标路径属于个人回收站，会剥离非个人小组绑定、资源独立权限和已计算小组 ACL。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；当前用户不是资源所有者 -> ResourceError.RESOURCE_PERMISSION_DENIED；标签不存在或不属于个人空间 -> ResourceError.TAG_NODE_NOT_FOUND；目标标签不是路径标签 -> ResourceError.RESOURCE_TAG_TYPE_INVALID；搜索索引同步失败 -> ResourceError.RESOURCE_SEARCH_FAILED。
                    - 响应：返回本次请求中的资源数量。
                    """
    )
    @Log(title = "移动个人资源路径标签", businessType = BusinessType.UPDATE)
    @PostMapping("/setPersonalResourcesPathTag")
    public R<ResourcePlacementResponse> setPersonalResourcesPathTag(
            @Validated @RequestBody ResourcePlacementPersonalPathTagRequest req) {
        String userId = SecurityContextHolder.getUserId().toString();
        // 资源所有者可以修改资源挂载的个人标签
        resourceService.assertResourceOwner(req.getResourceIds(), userId);
        resourcePlacementService.setPersonalResourcesPathTag(req.getResourceIds(), userId, req.getTargetPathTagId());
        return R.ok(new ResourcePlacementResponse(req.getResourceIds().size()));
    }

    @Operation(
            summary = "移动个人资源到回收站",
            description = """
                    - 用途：将个人空间中的资源移动到当前用户个人回收站。
                    - 请求：resourceIds 指定目标资源列表。
                    - 约束：当前用户必须是每个目标资源的所有者；当前用户个人空间必须存在回收站节点。
                    - 处理：由服务解析当前用户个人回收站路径标签，并替换资源首位路径标签；同时剥离非个人小组绑定、资源独立权限和已计算小组 ACL。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；当前用户不是资源所有者 -> ResourceError.RESOURCE_PERMISSION_DENIED；回收站节点不存在 -> ResourceError.TAG_NODE_NOT_FOUND；搜索索引同步失败 -> ResourceError.RESOURCE_SEARCH_FAILED。
                    - 响应：返回本次请求中的资源数量。
                    """
    )
    @Log(title = "移动个人资源到回收站", businessType = BusinessType.UPDATE)
    @PostMapping("/movePersonalResourcesToTrash")
    public R<ResourcePlacementResponse> movePersonalResourcesToTrash(
            @Validated @RequestBody ResourcePlacementPersonalTrashRequest req) {
        String userId = SecurityContextHolder.getUserId().toString();
        // 资源所有者可以修改资源挂载的个人标签
        resourceService.assertResourceOwner(req.getResourceIds(), userId);
        resourcePlacementService.movePersonalResourcesToTrash(req.getResourceIds(), userId);
        return R.ok(new ResourcePlacementResponse(req.getResourceIds().size()));
    }

    @Operation(
            summary = "替换个人资源普通标签",
            description = """
                    - 用途：全量替换个人空间中资源的普通标签集合。
                    - 请求：resourceIds 指定目标资源列表；normalTagIds 指定新的普通标签列表，空列表表示清空普通标签。
                    - 约束：当前用户必须是每个目标资源的所有者；normalTagIds 中的标签必须属于当前用户个人空间且不能是路径标签。
                    - 处理：保留资源在个人空间绑定列表中的首位路径标签，只替换其后的普通标签；不修改资源文件内容、小组挂载或资源权限。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；当前用户不是资源所有者 -> ResourceError.RESOURCE_PERMISSION_DENIED；标签不存在或不属于个人空间 -> ResourceError.TAG_NODE_NOT_FOUND；普通标签传入路径标签 -> ResourceError.RESOURCE_TAG_TYPE_INVALID。
                    - 响应：返回本次请求中的资源数量。
                    """
    )
    @Log(title = "替换个人资源普通标签", businessType = BusinessType.UPDATE)
    @PostMapping("/replacePersonalNormalTags")
    public R<ResourcePlacementResponse> replacePersonalNormalTags(
            @Validated @RequestBody ResourcePlacementPersonalNormalTagsRequest req) {
        String userId = SecurityContextHolder.getUserId().toString();
        // 资源所有者可以修改资源挂载的个人标签
        resourceService.assertResourceOwner(req.getResourceIds(), userId);
        resourcePlacementService.replacePersonalNormalTags(req.getResourceIds(), userId, req.getNormalTagIds());
        return R.ok(new ResourcePlacementResponse(req.getResourceIds().size()));
    }

    @Operation(
            summary = "挂载资源到小组标签",
            description = """
                    - 用途：将资源挂载到指定小组标签下。
                    - 请求：groupId 指定目标小组；resourceIds 指定目标资源列表；targetTagId 指定要新增的目标小组标签。
                    - 约束：不能直接挂载 Market 组标签；小组 OWNER、ADMIN 可操作，普通成员必须是所有目标资源的所有者并满足目标标签挂载权限。
                    - 处理：向资源在目标小组下的绑定列表追加 targetTagId，已存在时不重复追加；小组 FOLDER 模式下仍保持同组单标签约束；变更后触发资源 ACL 重算。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；当前用户不是资源所有者 -> ResourceError.RESOURCE_PERMISSION_DENIED；标签不存在或不属于目标小组 -> ResourceError.TAG_NODE_NOT_FOUND；直接绑定 Market 组标签 -> ResourceError.CANNOT_BIND_MARKET_GROUP_TAG_DIRECTLY；普通成员无标签挂载权限 -> ResourceError.BIND_RESOURCE_TO_TAG_NODE_DENIED；小组 FOLDER 模式下绑定多个标签 -> ResourceError.CANNOT_BIND_MULTIPLE_RESOURCE_TAGS_IN_FOLDER_MODE。
                    - 响应：返回本次请求中的资源数量。
                    """
    )
    @Log(title = "挂载资源到小组标签", businessType = BusinessType.UPDATE)
    @PostMapping("/mountResourcesToGroup")
    public R<ResourcePlacementResponse> mountResourcesToGroup(
            @Validated @RequestBody ResourcePlacementGroupMountRequest req) {
        String userId = SecurityContextHolder.getUserId().toString();
        if (req.getGroupId().startsWith(ResourceConstants.MARKET_GROUP_PREFIX)) {
            throw new ServiceException(ResourceError.CANNOT_BIND_MARKET_GROUP_TAG_DIRECTLY);
        }
        // 资源所有者或小组管理员可以修改资源挂载的小组标签
        GroupRoleType groupRole = SecurityContextHolder.getGroupRole(Long.parseLong(req.getGroupId()));
        if (groupRole != GroupRoleType.ADMIN && groupRole != GroupRoleType.OWNER) {
            // 非小组管理员不能添加或修改资源挂载的小组标签，除非是资源所有者且拥有该标签的资源挂载权限
            resourceService.assertResourceOwner(req.getResourceIds(), userId);
        }
        resourcePlacementService.mountResourcesToGroup(
                req.getResourceIds(), req.getGroupId(), userId, groupRole, req.getTargetTagId());
        return R.ok(new ResourcePlacementResponse(req.getResourceIds().size()));
    }

    @Operation(
            summary = "解除资源小组标签挂载",
            description = """
                    - 用途：从指定小组标签下解除资源挂载。
                    - 请求：groupId 指定目标小组；resourceSourceTagMap 按 resourceId -> sourceTagId 指定每个资源要解除的来源挂载点。
                    - 约束：不能直接操作 Market 组标签；小组 OWNER、ADMIN 可操作，普通成员必须是所有目标资源的所有者。
                    - 处理：按 resourceSourceTagMap 精确解除资源在目标小组下的指定来源标签挂载；解除首标签时会解除该资源在该小组下的全部挂载；变更后触发资源 ACL 重算。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；当前用户不是资源所有者 -> ResourceError.RESOURCE_PERMISSION_DENIED；标签不存在或不属于目标小组 -> ResourceError.TAG_NODE_NOT_FOUND；直接绑定 Market 组标签 -> ResourceError.CANNOT_BIND_MARKET_GROUP_TAG_DIRECTLY。
                    - 响应：返回本次请求中的资源数量。
                    """
    )
    @Log(title = "解除资源小组标签挂载", businessType = BusinessType.UPDATE)
    @PostMapping("/unmountResourcesToGroup")
    public R<ResourcePlacementResponse> unmountResourcesToGroup(
            @Validated @RequestBody ResourcePlacementGroupUnmountRequest req) {
        String userId = SecurityContextHolder.getUserId().toString();
        if (req.getGroupId().startsWith(ResourceConstants.MARKET_GROUP_PREFIX)) {
            throw new ServiceException(ResourceError.CANNOT_BIND_MARKET_GROUP_TAG_DIRECTLY);
        }
        // 资源所有者或小组管理员可以修改资源挂载的小组标签
        GroupRoleType groupRole = SecurityContextHolder.getGroupRole(Long.parseLong(req.getGroupId()));
        if (groupRole != GroupRoleType.ADMIN && groupRole != GroupRoleType.OWNER) {
            // 非小组管理员不能添加或修改资源挂载的小组标签，除非是资源所有者且拥有该标签的资源挂载权限
            resourceService.assertResourceOwner(new ArrayList<>(req.getResourceSourceTagMap().keySet()), userId);
        }
        resourcePlacementService.unmountResourcesToGroup(
                req.getResourceSourceTagMap(), req.getGroupId(), userId, groupRole);
        return R.ok(new ResourcePlacementResponse(req.getResourceSourceTagMap().size()));
    }

    @Operation(
            summary = "移动资源小组标签挂载",
            description = """
                    - 用途：将资源在小组中的指定来源挂载点移动到目标标签。
                    - 请求：groupId 指定目标小组；resourceSourceTagMap 按 resourceId -> sourceTagId 指定每个资源当前要移动的来源挂载点；targetTagId 指定新的小组标签。
                    - 约束：不能直接操作 Market 组标签；小组 OWNER、ADMIN 可操作，普通成员必须是所有目标资源的所有者并满足目标标签挂载权限。
                    - 处理：在资源原来源标签的位置替换为 targetTagId；如果资源已挂载 targetTagId，则只移除 sourceTagId；小组 FOLDER 模式下仍保持同组单标签约束；变更后触发资源 ACL 重算。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；当前用户不是资源所有者 -> ResourceError.RESOURCE_PERMISSION_DENIED；标签不存在或不属于目标小组 -> ResourceError.TAG_NODE_NOT_FOUND；直接绑定 Market 组标签 -> ResourceError.CANNOT_BIND_MARKET_GROUP_TAG_DIRECTLY；普通成员无标签挂载权限 -> ResourceError.BIND_RESOURCE_TO_TAG_NODE_DENIED；小组 FOLDER 模式下绑定多个标签 -> ResourceError.CANNOT_BIND_MULTIPLE_RESOURCE_TAGS_IN_FOLDER_MODE。
                    - 响应：返回本次请求中的资源数量。
                    """
    )
    @Log(title = "移动资源小组标签挂载", businessType = BusinessType.UPDATE)
    @PostMapping("/moveResourcesInGroup")
    public R<ResourcePlacementResponse> moveResourcesInGroup(
            @Validated @RequestBody ResourcePlacementGroupMoveRequest req) {
        String userId = SecurityContextHolder.getUserId().toString();
        if (req.getGroupId().startsWith(ResourceConstants.MARKET_GROUP_PREFIX)) {
            throw new ServiceException(ResourceError.CANNOT_BIND_MARKET_GROUP_TAG_DIRECTLY);
        }
        // 资源所有者或小组管理员可以修改资源挂载的小组标签
        GroupRoleType groupRole = SecurityContextHolder.getGroupRole(Long.parseLong(req.getGroupId()));
        if (groupRole != GroupRoleType.ADMIN && groupRole != GroupRoleType.OWNER) {
            // 非小组管理员不能添加或修改资源挂载的小组标签，除非是资源所有者且拥有该标签的资源挂载权限
            resourceService.assertResourceOwner(new ArrayList<>(req.getResourceSourceTagMap().keySet()), userId);
        }
        resourcePlacementService.moveResourcesInGroup(
                req.getResourceSourceTagMap(), req.getGroupId(), userId, groupRole, req.getTargetTagId());
        return R.ok(new ResourcePlacementResponse(req.getResourceSourceTagMap().size()));
    }
}
