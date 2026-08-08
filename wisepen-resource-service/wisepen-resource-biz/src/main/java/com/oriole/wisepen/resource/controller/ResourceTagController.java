package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.resource.domain.base.TagSpaceBase;
import com.oriole.wisepen.resource.domain.dto.req.TagCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.TagDeleteRequest;
import com.oriole.wisepen.resource.domain.dto.req.TagMoveRequest;
import com.oriole.wisepen.resource.domain.dto.req.TagUpdateRequest;
import com.oriole.wisepen.resource.domain.dto.res.TagTreeResponse;
import com.oriole.wisepen.resource.service.ITagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import static com.oriole.wisepen.resource.constant.ResourceConstants.MARKET_GROUP_PREFIX;
import static com.oriole.wisepen.resource.constant.ResourceConstants.PERSONAL_GROUP_PREFIX;

@Tag(name = "资源标签", description = "个人与小组资源标签的树形维护、权限配置和回收站处理")
@RestController
@RequestMapping("/resource/tag")
@RequiredArgsConstructor
@CheckRole
public class ResourceTagController {

    private final ITagService tagService;

    // 获取指定用户组的完整 Tag 树
    @Operation(
            summary = "获取标签树",
            description = """
                    - 用途：获取个人或小组资源空间的完整标签树，供资源浏览、挂载和标签维护使用。
                    - 请求：groupId 为空表示当前用户个人标签空间，非空表示指定小组标签空间。
                    - 约束：个人空间要求当前用户已登录；小组空间要求当前用户属于目标小组。
                    - 处理：一次性读取目标空间的全部标签并在内存中组装树；个人空间缺少系统根节点或回收站节点时会自动初始化；不修改普通业务标签。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不属于目标小组 -> PermissionError.PERMISSION_DENIED。
                    - 响应：返回标签树根节点列表及其子节点；每个节点包含 tagCreator 和对应的 creatorInfo。
                    """
    )
    @GetMapping("/getTagTree")
    public R<List<TagTreeResponse>> getTagTree(@RequestParam(required = false, value = "groupId") String groupId) {
        TagSpaceBase tagSpaceBase = new TagSpaceBase(groupId);
        checkPermission(tagSpaceBase, false);
        return R.ok(tagService.getTagTree(tagSpaceBase.getGroupId()));
    }

    // 创建 Tag 节点
    @Operation(
            summary = "创建标签",
            description = """
                    - 用途：在个人或小组资源空间的指定父节点下创建新的资源标签。
                    - 请求：groupId 为空表示个人标签空间，非空表示小组标签空间；parentId 为空或 0 表示创建在根层级；tagName 为新标签名称，其余字段描述标签可见性、挂载权限和默认动作。
                    - 约束：小组空间写入要求当前用户是 OWNER 或 ADMIN；不能在回收站及其子目录下创建标签；不能使用系统根节点或回收站保留名称；同一父节点下名称必须唯一。
                    - 处理：根据父节点继承路径标签类型并计算 ancestors，并记录当前用户为标签创建者；个人标签会清空所有标签权限配置；创建成功后只新增标签节点，不自动移动资源。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；小组写入权限不足 -> PermissionError.PERMISSION_DENIED；父节点不存在 -> ResourceError.PARENT_TAG_NODE_NOT_FOUND；同级重名 -> ResourceError.TAG_NODE_NAME_CONFLICT；使用系统保留名称 -> ResourceError.CANNOT_USE_RESERVED_TAG_PATH_NODE_NAME；在回收站下创建 -> ResourceError.CANNOT_OPERATE_TRASHED_TAG_PATH_NODE。
                    - 响应：返回新建标签 ID。
                    """
    )
    @Log(title = "创建标签", businessType = BusinessType.INSERT)
    @PostMapping("/addTag")
    public R<String> createTag(@Validated @RequestBody TagCreateRequest tagCreateRequest) {
        checkPermission(tagCreateRequest, true);
        return R.ok(tagService.createTag(tagCreateRequest, SecurityContextHolder.getUserId().toString()));
    }

    // 更新 Tag (重命名、修改可见性规则等)
    @Operation(
            summary = "更新标签",
            description = """
                    - 用途：维护标签名称、资源可见性规则、资源挂载权限和标签默认动作配置。
                    - 请求：targetTagId 指定目标标签；groupId 确定标签空间；tagName、权限范围、指定用户和 grantedActions 表达本次要更新的配置。
                    - 约束：小组空间写入要求当前用户是 OWNER 或 ADMIN；目标标签必须存在且不在回收站中；系统根节点和回收站节点不能修改；个人标签不能设置可见性或挂载权限；新名称不能使用保留名称且不能与同级标签重名。
                    - 处理：更新标签基本信息和权限配置，isPath 始终不允许由本接口改变；小组标签权限发生变化时通知挂载在该标签及其子孙标签下的资源重新计算 ACL；不移动标签位置。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；小组写入权限不足 -> PermissionError.PERMISSION_DENIED；标签不存在 -> ResourceError.TAG_NODE_NOT_FOUND；系统标签被修改 -> ResourceError.CANNOT_MODIFY_SYSTEM_TAG_PATH_NODE；个人标签设置权限 -> ResourceError.CANNOT_SET_TAG_NODE_VISIBILITY；同级重名 -> ResourceError.TAG_NODE_NAME_CONFLICT；使用系统保留名称 -> ResourceError.CANNOT_USE_RESERVED_TAG_PATH_NODE_NAME；回收站内标签被修改 -> ResourceError.CANNOT_OPERATE_TRASHED_TAG_PATH_NODE。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "修改标签", businessType = BusinessType.UPDATE)
    @PostMapping("/changeTag")
    public R<Void> updateTag(@Validated @RequestBody TagUpdateRequest tagUpdateRequest) {
        checkPermission(tagUpdateRequest, true);
        tagService.updateTag(tagUpdateRequest);
        return R.ok();
    }

    // 拖拽移动 Tag (改变树形层级结构)
    @Operation(
            summary = "移动标签",
            description = """
                    - 用途：调整个人或小组资源标签在树形结构中的父子层级。
                    - 请求：targetTagIds 指定一批被移动标签；newParentId 为空或 0 表示移动到根层级；groupId 确定标签空间。
                    - 约束：小组空间写入要求当前用户是 OWNER 或 ADMIN；被移动标签不能在同一批次中互为祖先和子孙；目标标签不能是系统根节点或回收站节点；不能移动到自身或自身子孙节点下；不能跨路径标签和普通标签类型移动；不能移动到回收站内部节点。
                    - 处理：去重后，批量更新目标标签和全部子孙标签的 parentId 与 ancestors；移动到个人回收站节点时发布回收站事件以剥离相关资源小组权限，普通移动会触发受影响资源 ACL 重算；不修改资源文件内容。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；小组写入权限不足 -> PermissionError.PERMISSION_DENIED；目标标签不存在 -> ResourceError.TAG_NODE_NOT_FOUND；批量目标包含祖先和子孙节点 -> ResourceError.CANNOT_OPERATE_ANCESTOR_DESCENDANT_TAG_NODES；父标签不存在 -> ResourceError.PARENT_TAG_NODE_NOT_FOUND；移动到自身 -> ResourceError.CANNOT_MOVE_TAG_NODE_TO_SELF；移动到子孙节点 -> ResourceError.CANNOT_MOVE_TAG_NODE_TO_DESCENDANT；跨路径/普通标签类型移动 -> ResourceError.CANNOT_MOVE_TAG_NODE_ACROSS_TAG_TYPE；同级重名 -> ResourceError.TAG_NODE_NAME_CONFLICT；系统标签被移动 -> ResourceError.CANNOT_MOVE_SYSTEM_TAG_PATH_NODE；回收站内标签被移动 -> ResourceError.CANNOT_OPERATE_TRASHED_TAG_PATH_NODE。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "移动标签", businessType = BusinessType.UPDATE)
    @PostMapping("/moveTags")
    public R<Void> moveTags(@Validated @RequestBody TagMoveRequest tagMoveRequest) {
        checkPermission(tagMoveRequest, true);
        tagService.moveTags(tagMoveRequest);
        return R.ok();
    }

    // 级联删除 Tag 及其子孙节点
    @Operation(
            summary = "级联删除标签",
            description = """
                    - 用途：删除个人或小组资源空间中的指定标签及其所有子孙标签。
                    - 请求：targetTagIds 指定一批待删除标签；groupId 确定标签空间。
                    - 约束：小组空间写入要求当前用户是 OWNER 或 ADMIN；待删除标签不能在同一批次中互为祖先和子孙；系统根节点和回收站节点不能删除；路径标签在未强制删除且未进入回收站前不能直接删除。
                    - 处理：去重后，批量删除目标标签和所有子孙标签，并按路径标签和普通标签拆分发布删除事件；下游会根据标签类型清理资源绑定，路径标签删除可能导致相关资源进入后续软删除流程。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；小组写入权限不足 -> PermissionError.PERMISSION_DENIED；标签不存在 -> ResourceError.TAG_NODE_NOT_FOUND；批量目标包含祖先和子孙节点 -> ResourceError.CANNOT_OPERATE_ANCESTOR_DESCENDANT_TAG_NODES；系统标签被删除 -> ResourceError.CANNOT_DELETE_SYSTEM_TAG_PATH_NODE；路径标签直接删除 -> ResourceError.CANNOT_DELETE_TAG_PATH_NODE_DIRECTLY。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "删除标签", businessType = BusinessType.DELETE)
    @PostMapping("/removeTags")
    public R<Void> deleteTags(@Validated @RequestBody TagDeleteRequest tagDeleteRequest) {
        checkPermission(tagDeleteRequest, true);
        tagService.deleteTags(tagDeleteRequest, false);
        return R.ok();
    }

    private void checkPermission(TagSpaceBase tagSpaceBase, boolean isWriteOp) {
        if (!StringUtils.hasText(tagSpaceBase.getGroupId())) {
            tagSpaceBase.setGroupId(PERSONAL_GROUP_PREFIX + SecurityContextHolder.getUserId()); // 个人私有空间 (p_开头)
        } else { // 正常群组
            String rawGroupId = tagSpaceBase.getGroupId().startsWith(MARKET_GROUP_PREFIX)
                    ? tagSpaceBase.getGroupId().substring(MARKET_GROUP_PREFIX.length())
                    : tagSpaceBase.getGroupId();
            if (isWriteOp){ // 写操作，必须是群组的 Admin 或 Owner
                SecurityContextHolder.assertGroupRole(Long.valueOf(rawGroupId), GroupRoleType.OWNER, GroupRoleType.ADMIN);
            } else { // 读操作，必须是群组成员
                SecurityContextHolder.assertInGroup(Long.valueOf(rawGroupId));
            }
        }
    }
}
