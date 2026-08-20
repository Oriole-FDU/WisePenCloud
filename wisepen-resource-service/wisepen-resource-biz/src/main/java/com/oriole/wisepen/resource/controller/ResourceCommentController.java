package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.resource.domain.dto.req.CommentCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.CommentReplyCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.CommentDeleteRequest;
import com.oriole.wisepen.resource.domain.dto.req.CommentLikeRequest;
import com.oriole.wisepen.resource.domain.dto.res.ResourceCommentItemResponse;
import com.oriole.wisepen.resource.enums.CommentSortBy;
import com.oriole.wisepen.resource.service.IResourceCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "资源评论", description = "资源评论区的发布、删除、点赞与分页查询")
@RestController
@RequestMapping("/resource/comment")
@RequiredArgsConstructor
@CheckRole
@Validated
public class ResourceCommentController {

    private final IResourceCommentService commentService;

    @Operation(
            summary = "发布顶级评论",
            description = """
                    - 用途：用户在资源展示页发布顶级评论。
                    - 请求：resourceId 指定目标资源；content 为评论正文，不能为空；imageUrls 为可选图片 URL 列表。
                    - 约束：当前用户必须已登录；目标资源必须存在且未被软删除。
                    - 处理：插入顶级评论文档，authorId 取当前登录用户；资源 commentCount +1。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；目标资源不存在或已删除 -> ResourceError.RESOURCE_NOT_FOUND。
                    - 响应：返回服务端生成的 commentId。
                    """
    )
    @PostMapping("/createComment")
    @Log(title = "发布顶级评论", businessType = BusinessType.INSERT)
    public R<String> createComment(@Validated @RequestBody CommentCreateRequest request) {
        String operatorUserId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        return R.ok(commentService.createComment(request, operatorUserId, groupRoles));
    }

    @Operation(
            summary = "发布回复",
            description = """
                    - 用途：用户对某条顶级评论或某条回复进行回复。
                    - 请求：resourceId 指定目标资源；replyTo 为同一资源下被回复目标的 commentId；content 不能为空；imageUrls 可选。
                    - 约束：当前用户必须已登录；目标资源必须存在且未被软删除；replyTo 对应目标必须存在且未被软删除。
                    - 处理：创建统一评论文档，按父目标类型写入 commentType、rootCommentId、replyTo 和 replyToUserId；所属顶级评论 replyCount +1；资源 commentCount +1。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；目标资源不存在或已删除 -> ResourceError.RESOURCE_NOT_FOUND；replyTo 对应目标不存在或已删除 -> ResourceError.COMMENT_NOT_FOUND。
                    - 响应：返回服务端生成的 commentId。
                    """
    )
    @PostMapping("/createReply")
    @Log(title = "发布回复", businessType = BusinessType.INSERT)
    public R<String> createReply(@Validated @RequestBody CommentReplyCreateRequest request) {
        String operatorUserId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        return R.ok(commentService.createReply(request, operatorUserId, groupRoles));
    }

    @Operation(
            summary = "删除评论或回复",
            description = """
                    - 用途：用户软删除自己发布的顶级评论或回复。
                    - 请求：resourceId 指定目标资源；commentId 为目标评论或回复的统一评论 ID。
                    - 约束：当前用户必须已登录；目标资源必须存在且未被软删除；目标评论必须属于该资源且未被软删除；操作人必须是管理员、资源所有者或该评论的 authorId。
                    - 处理（顶级评论）：软删除评论；资源 commentCount -1；不级联删除回复。
                    - 处理（回复）：软删除回复；所属顶级评论 replyCount -1；资源 commentCount -1。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；目标资源不存在或已删除 -> ResourceError.RESOURCE_NOT_FOUND；目标评论或回复不存在或已删除 -> ResourceError.COMMENT_NOT_FOUND；操作人无删除权限 -> ResourceError.COMMENT_DELETE_ACCESS_DENIED。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/deleteCommentItem")
    @Log(title = "删除评论或回复", businessType = BusinessType.DELETE)
    public R<Void> deleteCommentItem(@Validated @RequestBody CommentDeleteRequest request) {
        String operatorUserId = SecurityContextHolder.getUserId().toString();
        IdentityType operatorIdentityType = SecurityContextHolder.getIdentityType();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        commentService.deleteCommentItem(request, operatorUserId, operatorIdentityType, groupRoles);
        return R.ok();
    }

    @Operation(
            summary = "切换评论点赞状态",
            description = """
                    - 用途：用户对顶级评论或回复进行点赞或取消点赞（切换语义）。
                    - 请求：resourceId 指定目标资源；commentId 为目标评论或回复的统一评论 ID。
                    - 约束：当前用户必须已登录；目标资源必须存在且未被软删除；目标评论/回复必须属于该资源且未被软删除。
                    - 处理：$addToSet/$pull 维护 likedCommentIds；同步 $inc 目标 likeCount。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；目标资源不存在或已删除 -> ResourceError.RESOURCE_NOT_FOUND；目标评论或回复不存在或已删除 -> ResourceError.COMMENT_NOT_FOUND。
                    - 响应：返回操作后的点赞状态（true 表示已点赞）。
                    """
    )
    @PostMapping("/toggleLike")
    @Log(title = "切换评论点赞状态", businessType = BusinessType.UPDATE)
    public R<Boolean> toggleLike(@Validated @RequestBody CommentLikeRequest request) {
        String operatorUserId = SecurityContextHolder.getUserId().toString();
        return R.ok(commentService.toggleLike(request, operatorUserId));
    }

    @Operation(
            summary = "分页查询顶级评论",
            description = """
                    - 用途：加载资源评论区的顶级评论列表。
                    - 请求：resourceId 必传；sortBy 按 CommentSortBy 传 CREATE_TIME 或 LIKE_COUNT；page 从 1 开始；size 默认 10，最大 50。
                    - 约束：当前用户必须已登录。
                    - 处理：按页码分页查询顶级评论（含已软删除），批量补 authorInfo；不内嵌回复列表。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：PageR 含顶级评论列表、total、page 和 size。
                    """
    )
    @GetMapping("/listComments")
    public R<PageR<ResourceCommentItemResponse>> listComments(
            @NotBlank @RequestParam String resourceId,
            @RequestParam CommentSortBy sortBy,
            @Min(1) @Max(50) @RequestParam(defaultValue = "10") int size,
            @Min(1) @RequestParam(defaultValue = "1") int page) {
        return R.ok(commentService.listComments(resourceId, sortBy, size, page));
    }

    @Operation(
            summary = "分页查询回复列表",
            description = """
                    - 用途：展开某顶级评论的回复弹窗，加载该评论下全部回复。
                    - 请求：rootCommentId 必传；page 从 1 开始；size 默认 10，最大 50。
                    - 约束：当前用户必须已登录。
                    - 处理：按时间从新到旧页码分页查询回复，批量补 authorInfo 和 replyToUserInfo；回复列表平铺返回。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：PageR 含回复列表、total、page 和 size。
                    """
    )
    @GetMapping("/listReplies")
    public R<PageR<ResourceCommentItemResponse>> listReplies(
            @NotBlank @RequestParam String rootCommentId,
            @Min(1) @Max(50) @RequestParam(defaultValue = "10") int size,
            @Min(1) @RequestParam(defaultValue = "1") int page) {
        return R.ok(commentService.listReplies(rootCommentId, size, page));
    }
}
