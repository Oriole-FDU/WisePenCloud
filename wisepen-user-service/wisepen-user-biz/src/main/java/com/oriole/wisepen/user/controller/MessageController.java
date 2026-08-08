package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.user.api.domain.dto.req.MessageReadRequest;
import com.oriole.wisepen.user.api.domain.dto.req.MessageRemoveRequest;
import com.oriole.wisepen.user.api.domain.dto.res.MessageInfoResponse;
import com.oriole.wisepen.user.service.IMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户消息", description = "当前用户站内消息查询、未读数、已读与删除")
@RestController
@RequestMapping("/user/message")
@RequiredArgsConstructor
@Validated
@CheckRole
public class MessageController {

    private final IMessageService messageService;

    @Operation(
            summary = "分页查询用户消息",
            description = """
                    - 用途：查询当前用户站内消息中心列表，用于消息中心页面和消息弹层，包含直接投递消息与当前用户可见的全员系统消息。
                    - 请求：messageType 按消息类型过滤；readStatus 按已读状态过滤，未传时查询全部；page 和 size 控制分页。
                    - 约束：当前用户必须已登录；只能查询当前用户自己的未删除消息。
                    - 处理：先将当前用户可见但尚未入箱的全员系统消息批量同步到个人收件箱，再按个人收件箱分页返回；不修改消息已读或删除状态。
                    - 失败：无明确业务失败点。
                    - 响应：返回当前用户消息分页列表。
                    """
    )
    @Log(title = "分页查询用户消息", businessType = BusinessType.SELECT, isSaveResponseData = false)
    @GetMapping("/listMessages")
    public R<PageR<MessageInfoResponse>> listMessages(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) Integer page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) Integer size
    ) {
        return R.ok(messageService.listMessages(SecurityContextHolder.getUserId(), page, size));
    }

    @Operation(
            summary = "获取未读消息数",
            description = """
                    - 用途：为导航栏、侧边栏或消息入口展示当前用户未读站内消息数量，包含直接投递消息与当前用户可见的全员系统消息。
                    - 请求：无业务请求参数，目标用户来自当前认证上下文。
                    - 约束：当前用户必须已登录；只统计当前用户自己的未删除消息。
                    - 处理：先将当前用户可见但尚未入箱的全员系统消息批量同步到个人收件箱，再统计未删除且未读的收件箱记录；不读取消息正文或修改已读状态。
                    - 失败：无明确业务失败点。
                    - 响应：返回当前用户未读消息数量。
                    """
    )
    @Log(title = "获取未读消息数", businessType = BusinessType.SELECT, isSaveResponseData = false)
    @GetMapping("/getUnreadMessageCount")
    public R<Long> getUnreadMessageCount() {
        return R.ok(messageService.getUnreadMessageCount(SecurityContextHolder.getUserId()));
    }

    @Operation(
            summary = "批量标记消息已读",
            description = """
                    - 用途：用户打开或确认一条或多条站内消息后，将其从未读状态转为已读状态。
                    - 请求：messageIds 指定目标消息 ID 列表。
                    - 约束：当前用户必须已登录；目标消息只会更新当前用户未删除的收件箱记录。
                    - 处理：批量更新当前用户收件箱中指定未读消息的 readTime；不修改消息主体或其他用户的收件箱状态。
                    - 失败：无明确业务失败点。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "批量标记消息已读", businessType = BusinessType.UPDATE)
    @PostMapping("/readMessage")
    public R<Void> readMessage(@RequestBody @Valid MessageReadRequest req) {
        messageService.readMessages(SecurityContextHolder.getUserId(), req.getMessageIds());
        return R.ok();
    }

    @Operation(
            summary = "全部消息已读",
            description = """
                    - 用途：用户在消息中心一键清空未读状态。
                    - 请求：无业务请求参数，目标用户来自当前认证上下文。
                    - 约束：当前用户必须已登录；只处理当前用户自己的未删除消息。
                    - 处理：先将当前用户可见但尚未入箱的全员系统消息批量同步到个人收件箱，再批量更新当前用户未删除且未读的收件箱记录 readTime；不修改消息主体或其他用户收件箱状态。
                    - 失败：无明确业务失败点。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "全部消息已读", businessType = BusinessType.UPDATE)
    @PostMapping("/readAllMessages")
    public R<Void> readAllMessages() {
        messageService.readAllMessages(SecurityContextHolder.getUserId());
        return R.ok();
    }

    @Operation(
            summary = "删除用户消息",
            description = """
                    - 用途：用户从自己的消息中心移除一条站内消息。
                    - 请求：messageId 指定目标消息。
                    - 约束：当前用户必须已登录；目标消息必须属于当前用户且未被删除。
                    - 处理：必要时先同步当前用户可见的全员系统消息，再软删除当前用户收件箱记录；不删除消息主体或其他用户的收件箱记录。
                    - 失败：目标消息不存在或已删除 -> UserError.MESSAGE_NOT_FOUND。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "删除用户消息", businessType = BusinessType.DELETE)
    @PostMapping("/removeMessage")
    public R<Void> removeMessage(@RequestBody @Valid MessageRemoveRequest req) {
        messageService.removeMessage(SecurityContextHolder.getUserId(), req.getMessageId());
        return R.ok();
    }
}
