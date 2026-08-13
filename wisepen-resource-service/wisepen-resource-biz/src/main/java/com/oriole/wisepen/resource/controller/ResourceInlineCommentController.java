package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionReqDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionResDTO;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemDeleteRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemReactionDeleteRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemReactionSetRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemUpdateRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentResolveRequest;
import com.oriole.wisepen.resource.domain.dto.res.ResourceInlineCommentResponse;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.service.IResourceInlineCommentService;
import com.oriole.wisepen.resource.service.IResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "资源行内批注", description = "资源正文或预览载体中的行内批注创建、追加、表态、解决与一次性查询")
@RestController
@RequestMapping("/resource/inlineComment")
@RequiredArgsConstructor
@CheckRole
@Validated
public class ResourceInlineCommentController {

    private final IResourceInlineCommentService inlineCommentService;
    private final IResourceService resourceService;

    @Operation(
            summary = "创建行内批注",
            description = """
                    - 用途：在资源正文、PDF 预览或其他载体的指定锚点上创建一张行内批注卡片。
                    - 请求：resourceId 指定资源；contentVersion 是当前内容版本；applicableFromVersion 和 applicableToVersion 表示批注适用版本范围；externalAnchorId、quoteText 和 anchorPayload 构成锚点引用；content、imageUrls、mentionUserIds 构成首条消息。
                    - 约束：当前用户必须已登录；目标资源必须存在且未被软删除；当前用户必须拥有 ResourceAction.INLINE_COMMENT。
                    - 处理：创建独立 ResourceInlineCommentEntity，服务端生成 inlineCommentId 和首条 itemId；仅保存锚点引用，不解析 BlockNote、EmbedPDF 或 OnlyOffice 的 anchorPayload；不写入普通评论区和资源 commentCount。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源动作权限裁决未通过 -> ResourceError.RESOURCE_PERMISSION_DENIED。
                    - 响应：返回服务端生成的 inlineCommentId。
                    """
    )
    @PostMapping("/createInlineComment")
    @Log(title = "创建行内批注", businessType = BusinessType.INSERT)
    public R<String> createInlineComment(@Validated @RequestBody InlineCommentCreateRequest request) {
        String operatorUserId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        ResourceCheckPermissionResDTO permission = resourceService.checkPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(request.getResourceId())
                .userId(Long.valueOf(operatorUserId))
                .groupRoles(groupRoles)
                .targetVersion(request.getContentVersion())
                .build());
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.INLINE_COMMENT)) {
            throw new ServiceException(ResourceError.RESOURCE_PERMISSION_DENIED);
        }
        return R.ok(inlineCommentService.createInlineComment(request, operatorUserId));
    }

    @Operation(
            summary = "追加行内批注消息",
            description = """
                    - 用途：向已有行内批注卡片追加一条平铺消息。
                    - 请求：resourceId、inlineCommentId 和 contentVersion 定位当前内容版本下的批注卡片；content、imageUrls、mentionUserIds 构成新增消息。
                    - 约束：当前用户必须已登录；目标资源和行内批注必须存在；当前用户必须拥有 ResourceAction.INLINE_COMMENT。
                    - 处理：服务端生成 itemId 和时间字段，将消息追加到 items 列表；不创建评论-回复树，不写入普通评论区和资源 commentCount。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源动作权限裁决未通过 -> ResourceError.RESOURCE_PERMISSION_DENIED；行内批注不存在或不适用于当前内容版本 -> ResourceError.COMMENT_NOT_FOUND。
                    - 响应：返回服务端生成的 itemId。
                    """
    )
    @PostMapping("/addInlineCommentItem")
    @Log(title = "追加行内批注消息", businessType = BusinessType.INSERT)
    public R<String> addInlineCommentItem(@Validated @RequestBody InlineCommentItemCreateRequest request) {
        String operatorUserId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        ResourceCheckPermissionResDTO permission = resourceService.checkPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(request.getResourceId())
                .userId(Long.valueOf(operatorUserId))
                .groupRoles(groupRoles)
                .targetVersion(request.getContentVersion())
                .build());
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.INLINE_COMMENT)) {
            throw new ServiceException(ResourceError.RESOURCE_PERMISSION_DENIED);
        }
        return R.ok(inlineCommentService.addInlineCommentItem(request, operatorUserId));
    }

    @Operation(
            summary = "修改行内批注消息",
            description = """
                    - 用途：修改行内批注卡片中的某条消息。
                    - 请求：resourceId、inlineCommentId、itemId 和 contentVersion 定位当前内容版本下的消息；content、imageUrls、mentionUserIds 构成修改后的消息内容。
                    - 约束：当前用户必须已登录；目标资源、行内批注和消息必须存在；当前用户必须拥有 ResourceAction.INLINE_COMMENT；只有消息作者本人可以修改消息。
                    - 处理：整体替换目标消息的 content、imageUrls 和 mentionUserIds，更新消息 updateTime 和批注 updateTime；不改普通评论统计。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源动作权限裁决未通过 -> ResourceError.RESOURCE_PERMISSION_DENIED；行内批注或消息不存在，或不适用于当前内容版本 -> ResourceError.COMMENT_NOT_FOUND；操作人不是消息作者 -> ResourceError.COMMENT_UPDATE_ACCESS_DENIED。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/updateInlineCommentItem")
    @Log(title = "修改行内批注消息", businessType = BusinessType.UPDATE)
    public R<Void> updateInlineCommentItem(@Validated @RequestBody InlineCommentItemUpdateRequest request) {
        String operatorUserId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        ResourceCheckPermissionResDTO permission = resourceService.checkPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(request.getResourceId())
                .userId(Long.valueOf(operatorUserId))
                .groupRoles(groupRoles)
                .targetVersion(request.getContentVersion())
                .build());
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.INLINE_COMMENT)) {
            throw new ServiceException(ResourceError.RESOURCE_PERMISSION_DENIED);
        }
        inlineCommentService.updateInlineCommentItem(request, operatorUserId);
        return R.ok();
    }

    @Operation(
            summary = "设置行内批注消息表情",
            description = """
                    - 用途：让当前用户对行内批注卡片中的某条消息添加一个表情表态。
                    - 请求：resourceId、inlineCommentId、itemId 和 contentVersion 定位当前内容版本下的消息；emojiId 是前端选择的表情标识。
                    - 约束：当前用户必须已登录；目标资源、行内批注和消息必须存在；当前用户必须拥有 ResourceAction.INLINE_COMMENT。
                    - 处理：按当前用户 ID 追加 items.reactions 中对应表情；同一用户对同一消息可保留多个不同 emojiId；重复设置同一 emojiId 时保持幂等；不更新批注 updateTime，不改普通评论统计。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源动作权限裁决未通过 -> ResourceError.RESOURCE_PERMISSION_DENIED；行内批注或消息不存在，或不适用于当前内容版本 -> ResourceError.COMMENT_NOT_FOUND。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/setInlineCommentItemReaction")
    @Log(title = "设置行内批注消息表情", businessType = BusinessType.UPDATE)
    public R<Void> setInlineCommentItemReaction(@Validated @RequestBody InlineCommentItemReactionSetRequest request) {
        String operatorUserId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        ResourceCheckPermissionResDTO permission = resourceService.checkPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(request.getResourceId())
                .userId(Long.valueOf(operatorUserId))
                .groupRoles(groupRoles)
                .targetVersion(request.getContentVersion())
                .build());
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.INLINE_COMMENT)) {
            throw new ServiceException(ResourceError.RESOURCE_PERMISSION_DENIED);
        }
        inlineCommentService.setInlineCommentItemReaction(request, operatorUserId);
        return R.ok();
    }

    @Operation(
            summary = "取消行内批注消息表情",
            description = """
                    - 用途：让当前用户取消自己对某条行内批注消息的表情表态。
                    - 请求：resourceId、inlineCommentId、itemId 和 contentVersion 定位当前内容版本下的消息；emojiId 指定要取消的表情。
                    - 约束：当前用户必须已登录；目标资源、行内批注和消息必须存在；当前用户必须拥有 ResourceAction.INLINE_COMMENT。
                    - 处理：移除 items.reactions 中当前用户 ID 下指定 emojiId 的表情；未设置过该表情时保持幂等；不更新批注 updateTime，不改普通评论统计。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源动作权限裁决未通过 -> ResourceError.RESOURCE_PERMISSION_DENIED；行内批注或消息不存在，或不适用于当前内容版本 -> ResourceError.COMMENT_NOT_FOUND。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/deleteInlineCommentItemReaction")
    @Log(title = "取消行内批注消息表情", businessType = BusinessType.UPDATE)
    public R<Void> deleteInlineCommentItemReaction(@Validated @RequestBody InlineCommentItemReactionDeleteRequest request) {
        String operatorUserId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        ResourceCheckPermissionResDTO permission = resourceService.checkPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(request.getResourceId())
                .userId(Long.valueOf(operatorUserId))
                .groupRoles(groupRoles)
                .targetVersion(request.getContentVersion())
                .build());
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.INLINE_COMMENT)) {
            throw new ServiceException(ResourceError.RESOURCE_PERMISSION_DENIED);
        }
        inlineCommentService.deleteInlineCommentItemReaction(request, operatorUserId);
        return R.ok();
    }

    @Operation(
            summary = "删除行内批注消息",
            description = """
                    - 用途：删除行内批注卡片中的某条消息。
                    - 请求：resourceId、inlineCommentId 和 itemId 定位目标消息。
                    - 约束：当前用户必须已登录；目标资源、行内批注和消息必须存在；操作人必须是消息作者、资源所有者或平台管理员。
                    - 处理：从 items 中移除目标消息；如果该消息是卡片内最后一条消息，则删除整张行内批注卡片；不保留删除占位，不改普通评论统计。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；目标资源不存在或已删除 -> ResourceError.RESOURCE_NOT_FOUND；行内批注或消息不存在 -> ResourceError.COMMENT_NOT_FOUND；操作人无删除权限 -> ResourceError.COMMENT_DELETE_ACCESS_DENIED。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/deleteInlineCommentItem")
    @Log(title = "删除行内批注消息", businessType = BusinessType.DELETE)
    public R<Void> deleteInlineCommentItem(@Validated @RequestBody InlineCommentItemDeleteRequest request) {
        String operatorUserId = SecurityContextHolder.getUserId().toString();
        IdentityType operatorIdentityType = SecurityContextHolder.getIdentityType();
        inlineCommentService.deleteInlineCommentItem(request, operatorUserId, operatorIdentityType);
        return R.ok();
    }

    @Operation(
            summary = "变更行内批注解决状态",
            description = """
                    - 用途：将一张行内批注卡片标记为已解决，或恢复为未解决状态。
                    - 请求：resourceId、inlineCommentId 和 contentVersion 定位目标行内批注；resolved 表示目标解决状态，true 为解决，false 为取消解决。
                    - 约束：当前用户必须已登录；目标资源和行内批注必须存在；操作人必须是批注创建者、资源所有者、平台管理员或拥有 ResourceAction.EDIT。
                    - 处理：resolved 为 true 时设置 resolved、resolvedBy、resolvedAt 和 updateTime；resolved 为 false 时清空 resolvedBy 和 resolvedAt，并更新 updateTime；不删除 items，不改普通评论统计。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；目标资源不存在或已删除 -> ResourceError.RESOURCE_NOT_FOUND；行内批注不存在或不适用于当前内容版本 -> ResourceError.COMMENT_NOT_FOUND；操作人无解决权限 -> ResourceError.COMMENT_RESOLVE_ACCESS_DENIED。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/changeInlineCommentResolveStatus")
    @Log(title = "变更行内批注解决状态", businessType = BusinessType.UPDATE)
    public R<Void> changeInlineCommentResolveStatus(@Validated @RequestBody InlineCommentResolveRequest request) {
        String operatorUserId = SecurityContextHolder.getUserId().toString();
        IdentityType operatorIdentityType = SecurityContextHolder.getIdentityType();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        ResourceCheckPermissionResDTO permission = resourceService.checkPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(request.getResourceId())
                .userId(Long.valueOf(operatorUserId))
                .groupRoles(groupRoles)
                .targetVersion(request.getContentVersion())
                .build());
        boolean hasEditAction = permission != null
                && permission.getAllowedActions() != null
                && permission.getAllowedActions().contains(ResourceAction.EDIT);
        inlineCommentService.changeInlineCommentResolveStatus(request, operatorUserId, operatorIdentityType, hasEditAction);
        return R.ok();
    }

    @Operation(
            summary = "查询行内批注列表",
            description = """
                    - 用途：前端打开资源时一次性加载该资源的完整行内批注状态。
                    - 请求：resourceId 必传；contentVersion、resolved 可选；resolved 不传时返回全部行内批注。
                    - 约束：当前用户必须已登录；目标资源必须存在且未被软删除；当前用户必须拥有 ResourceAction.VIEW。
                    - 处理：按资源、适用版本范围和解决状态筛选批注，批量补 creatorInfo、resolvedByInfo、items.authorInfo 和 reactionGroups.users；不分页，不提供 getInlineCommentDetail，不解析 anchorPayload。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源查看权限裁决未通过 -> ResourceError.RESOURCE_PERMISSION_DENIED。
                    - 响应：返回 R<List<ResourceInlineCommentResponse>>，每条包含 inlineCommentId、resourceId、applicableFromVersion、applicableToVersion、creatorId、creatorInfo、anchorRef、items、resolved、resolvedBy、resolvedByInfo、resolvedAt、createTime 和 updateTime；items 包含 itemId、authorId、authorInfo、content、imageUrls、mentionUserIds、reactions、reactionGroups、createTime 和 updateTime；reactions 是 userId -> {emojiId、createTime、updateTime}，reactionGroups 每组包含 emojiId、count、reactedByCurrentUser 和 users。
                    """
    )
    @GetMapping("/listInlineComments")
    public R<List<ResourceInlineCommentResponse>> listInlineComments(
            @NotBlank @RequestParam String resourceId,
            @RequestParam(required = false) Integer contentVersion,
            @RequestParam(required = false) Boolean resolved) {
        String operatorUserId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        ResourceCheckPermissionResDTO permission = resourceService.checkPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(resourceId)
                .userId(Long.valueOf(operatorUserId))
                .groupRoles(groupRoles)
                .targetVersion(contentVersion)
                .build());
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.VIEW)) {
            throw new ServiceException(ResourceError.RESOURCE_PERMISSION_DENIED);
        }
        return R.ok(inlineCommentService.listInlineComments(resourceId, contentVersion, resolved, operatorUserId));
    }
}
