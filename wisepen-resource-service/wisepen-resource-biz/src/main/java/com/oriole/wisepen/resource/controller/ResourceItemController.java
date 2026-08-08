package com.oriole.wisepen.resource.controller;

import com.alibaba.nacos.common.utils.StringUtils;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.list.QueryLogicEnum;
import com.oriole.wisepen.common.core.domain.enums.list.SortDirectionEnum;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.resource.constant.ResourceConstants;
import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import com.oriole.wisepen.resource.domain.dto.req.BatchResourceUpdateTagsRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourceUpdateActionPermissionRequest;
import com.oriole.wisepen.resource.domain.dto.res.ResourceBaseInfoResponse;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.domain.dto.req.ResourceRenameRequest;
import com.oriole.wisepen.resource.enums.ResourceSortBy;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.service.IResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "资源", description = "资源重命名、标签绑定、独立权限和分页查询")
@RestController
@RequestMapping("/resource/item")
@RequiredArgsConstructor
@CheckRole
@Validated
public class ResourceItemController {

    private final IResourceService resourceService;

    @Operation(
            summary = "获取资源基础信息",
            description = """
                    - 用途：在资源详情、预览或跨业务展示场景中读取资源的基础展示信息。
                    - 请求：resourceId 指定目标资源。
                    - 约束：当前用户必须已登录；目标资源必须存在；当前用户必须拥有资源 DISCOVER 权限。
                    - 处理：查询资源主记录，按预计算 ACL 快速校验 DISCOVER 权限，并补充资源所有者的展示信息；普通 Market 购买权限不作为本接口授权来源；不返回标签绑定、资源权限覆盖、指定用户权限或市场销售信息。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；资源无发现权限 -> ResourceError.RESOURCE_PERMISSION_DENIED。
                    - 响应：返回资源 ID、名称、类型、所有者 ID、大小、互动统计和所有者展示信息；当前用户拥有 VIEW 权限时额外返回 preview。
                    """
    )
    @GetMapping("/getResourceBaseInfo")
    public R<ResourceBaseInfoResponse> getResourceInfo(@RequestParam String resourceId) {
        String currentUserId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        ResourceBaseInfoResponse response = resourceService.getResourceBaseInfo(resourceId, currentUserId, groupRoles);
        return R.ok(response);
    }

    // 重命名资源
    @Operation(
            summary = "重命名资源",
            description = """
                    - 用途：资源所有者修改资源在资源列表和搜索结果中展示的名称。
                    - 请求：resourceId 指定目标资源；newName 为新的资源名称。
                    - 约束：当前用户必须是资源所有者；目标资源必须存在。
                    - 处理：更新资源名称并同步搜索元数据中的资源名称；不修改资源类型、文件内容、标签绑定或权限配置。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；当前用户不是资源所有者 -> ResourceError.RESOURCE_PERMISSION_DENIED；搜索索引同步失败 -> ResourceError.RESOURCE_SEARCH_FAILED。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "重命名资源", businessType = BusinessType.UPDATE)
    @PostMapping("/renameResource")
    public R<Void> renameResource(@Validated @RequestBody ResourceRenameRequest req) {
        resourceService.assertResourceOwner(req.getResourceId(), SecurityContextHolder.getUserId().toString());
        resourceService.renameResource(req);
        return R.ok();
    }

    // 删除资源
    @Operation(
            summary = "删除资源",
            description = """
                    - 用途：资源所有者批量移除自己拥有的资源。
                    - 请求：resourceIds 为待删除资源 ID 列表。
                    - 约束：当前用户必须是每个目标资源的所有者；列表中的资源必须存在。
                    - 处理：批量校验所有权后执行软删除，将资源写入审计回收集合并从业务资源表移除；不在本接口直接抹除对象存储中的物理文件。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；任一资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；当前用户不是资源所有者 -> ResourceError.RESOURCE_PERMISSION_DENIED；搜索索引同步失败 -> ResourceError.RESOURCE_SEARCH_FAILED。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "删除资源", businessType = BusinessType.DELETE)
    @PostMapping("/removeResources")
    public R<Void> deleteResource(@RequestParam List<String> resourceIds) {
        String currentUserId = SecurityContextHolder.getUserId().toString();
        resourceService.assertResourceOwner(resourceIds, currentUserId);
        resourceService.softRemoveResources(resourceIds);
        return R.ok();
    }

    // 编辑资源的所属标签
    @Operation(
            summary = "批量更新资源标签",
            description = """
                    - 用途：调整资源挂载到个人标签空间或小组标签空间下的位置与标签集合。
                    - 请求：resourceIds 指定目标资源列表；groupId 为空表示个人标签空间，不为空表示小组标签空间；tagIds 按业务顺序给出目标标签列表。
                    - 约束：个人标签更新必须由资源所有者发起，且 tagIds 必须包含唯一的路径标签并位于首位；小组标签更新允许小组 OWNER、ADMIN 操作，普通成员必须同时是所有目标资源的所有者并满足标签挂载权限。
                    - 处理：个人空间会覆盖这些资源在个人标签空间的绑定；若移入个人回收站，会剥离非个人小组绑定、资源独立权限和已计算小组 ACL。小组空间会覆盖该小组下的绑定，并触发资源 ACL 重算；不修改资源文件内容。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；当前用户不是资源所有者 -> ResourceError.RESOURCE_PERMISSION_DENIED；标签不存在或不属于目标空间 -> ResourceError.TAG_NODE_NOT_FOUND；个人路径标签数量不唯一 -> ResourceError.CANNOT_BIND_RESOURCE_TO_MULTIPLE_PATH_NODES；个人路径标签未放在首位 -> ResourceError.CANNOT_PLACE_RESOURCE_PATH_TAG_AFTER_TAGS；小组 FOLDER 模式下绑定多个标签 -> ResourceError.CANNOT_BIND_MULTIPLE_RESOURCE_TAGS_IN_FOLDER_MODE；普通成员无标签挂载权限 -> ResourceError.BIND_RESOURCE_TO_TAG_NODE_DENIED；搜索索引同步失败 -> ResourceError.RESOURCE_SEARCH_FAILED。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "修改资源标签", businessType = BusinessType.UPDATE)
    @PostMapping("/changeResourceTags")
    public R<Void> updateResourceTags(@Validated @RequestBody BatchResourceUpdateTagsRequest req) {
        String userId = SecurityContextHolder.getUserId().toString();
        if (!StringUtils.hasText(req.getGroupId())) {
            // 资源所有者可以修改资源挂载的个人标签
            resourceService.assertResourceOwner(req.getResourceIds(), userId);
            resourceService.updatePersonalResourceTags(
                    req.getResourceIds(),
                    ResourceConstants.PERSONAL_GROUP_PREFIX + userId,
                    req.getTagIds()
            );
        } else {
            if (req.getGroupId().startsWith(ResourceConstants.MARKET_GROUP_PREFIX)) {
                throw new ServiceException(ResourceError.CANNOT_BIND_MARKET_GROUP_TAG_DIRECTLY);
            }
            // 资源所有者或小组管理员可以修改资源挂载的小组标签
            GroupRoleType groupRole = SecurityContextHolder.getGroupRole(Long.parseLong(req.getGroupId()));
            if (groupRole != GroupRoleType.ADMIN && groupRole != GroupRoleType.OWNER) {
                // 非小组管理员不能添加或修改资源挂载的小组标签，除非是资源所有者且拥有该标签的资源挂载权限
                resourceService.assertResourceOwner(req.getResourceIds(), userId);
            }
            resourceService.updateGroupResourceTags(
                    req.getResourceIds(),
                    req.getGroupId(),
                    userId,
                    groupRole,
                    req.getTagIds()
            );
        }
        return R.ok();
    }

    @Operation(
            summary = "更新资源独立权限",
            description = """
                    - 用途：资源所有者设置资源级动作权限覆盖规则和指定用户特权动作。
                    - 请求：resourceId 指定目标资源；overrideGrantedActions 是 groupId -> actions 的覆盖映射，null 表示清空普通组覆盖；specifiedUsersGrantedActions 为空表示清空指定用户特权。
                    - 约束：当前用户必须是资源所有者；目标资源必须存在；动作列表必须是合法资源动作枚举；Market group 覆盖不能通过本接口修改。
                    - 处理：保存资源级覆盖权限和指定用户权限映射，并触发资源 ACL 重算；Market 审核 override 会被保留；不修改资源标签绑定、默认小组权限或资源内容。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；当前用户不是资源所有者 -> ResourceError.RESOURCE_PERMISSION_DENIED。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "修改资源权限", businessType = BusinessType.UPDATE)
    @PostMapping("/changeResourceActionPermission")
    public R<Void> updateResourceActionPermission(@Validated @RequestBody ResourceUpdateActionPermissionRequest req) {
        String userId = SecurityContextHolder.getUserId().toString();

        resourceService.assertResourceOwner(req.getResourceId(), userId);
        resourceService.updateResourceActionPermission(req);
        return R.ok();
    }

    // 列出资源
    @Operation(
            summary = "分页查询资源",
            description = """
                    - 用途：按个人空间或小组空间分页查询当前用户可见的资源列表。
                    - 请求：groupId 为空时查询当前用户个人资源空间，非空时查询指定小组空间；tagIds 为空表示查询该空间下全部可见资源，非空时按 tagQueryLogicMode 进行多标签筛选；includeMyInteraction 控制是否补充当前用户交互状态；resourceType、page、size、sortBy、sortDir 控制类型过滤、分页和排序，size 最大为 100。
                    - 约束：当前用户必须已登录；查询小组空间时必须属于目标小组；page 不能小于 1；size 不能超过 100；排序字段、标签组合逻辑和资源类型必须合法。
                    - 处理：个人空间默认排除个人回收站体系下的资源，除非显式传入回收站内标签；小组空间按当前用户在小组中的角色和资源 ACL 查询具备 DISCOVER 的资源；批量补充 ownerInfo、按组分隔的 tagBinds、currentActions 和互动统计；按需批量补充当前用户交互状态；tagBinds.primaryTagId 表示对应组内物理位置主标签；当前 groupId 下的 marketOffers（买家仅见已审核通过且在售条目，资源所有者与小组管理员可见全部）；仅资源所有者返回 overrideGrantedActions 与 specifiedUsersGrantedActions；不返回当前用户无权发现的资源。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不属于目标小组 -> PermissionError.PERMISSION_DENIED。
                    - 响应：返回分页资源列表、总数，以及当前页资源的完整列表态 ResourceItemResponse。
                    """
    )
    @GetMapping("/listResources")
    public R<PageR<ResourceItemResponse>> listResources(
            @Parameter(description = "小组ID。查个人资源时必须留空")
            @RequestParam(value = "groupId", required = false) String groupId,
            @Parameter(description = "标签ID列表，为空时列出全部资源。配合 groupId 决定查个人的标签还是小组的标签。")
            @RequestParam(value = "tagIds", required = false) List<String> tagIds,
            @Parameter(description = "多标签组合查询时的逻辑关系(AND/OR)")
            @RequestParam(value = "tagQueryLogicMode", defaultValue = "OR") QueryLogicEnum tagQueryLogicMode,
            @RequestParam(value = "includeMyInteraction", defaultValue = "false") Boolean includeMyInteraction,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "page", defaultValue = "1") @Min(value = 1, message = ResourceValidationMsg.PAGE_MIN_INVALID) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(value = 1, message = ResourceValidationMsg.SIZE_MIN_INVALID) @Max(value = 100, message = ResourceValidationMsg.SIZE_MAX_INVALID) int size,
            @Parameter(description = "排序字段枚举")
            @RequestParam(value = "sortBy", defaultValue = "UPDATE_TIME") ResourceSortBy sortBy,
            @RequestParam(value = "sortDir", defaultValue = "DESC") SortDirectionEnum sortDir) {
        String userId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();

        GroupRoleType userGroupRole = null;
        if (!StringUtils.hasText(groupId)) {
            groupId = ResourceConstants.PERSONAL_GROUP_PREFIX + userId;
        } else {
            userGroupRole = SecurityContextHolder.assertInGroup(Long.valueOf(groupId));
        }

        PageR<ResourceItemResponse> result = resourceService.listResources(
                userId,
                groupId,
                userGroupRole,
                groupRoles,
                tagIds,
                tagQueryLogicMode,
                includeMyInteraction,
                resourceType,
                page,
                size,
                sortBy,
                sortDir
        );
        return R.ok(result);
    }
}
