package com.oriole.wisepen.system.controller;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.system.api.enums.FeedbackStatus;
import com.oriole.wisepen.system.api.enums.FeedbackType;
import com.oriole.wisepen.system.domain.entity.FeedbackEntity;
import com.oriole.wisepen.system.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理员 - 用户反馈", description = "管理员查询和处理用户工单")
@RestController
@RequestMapping("/admin/system/feedback")
@RequiredArgsConstructor
@CheckRole(IdentityType.ADMIN)
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @Operation(
            summary = "分页查询用户工单",
            description = """
                    - 用途：供管理员查看用户提交的全部工单。
                    - 请求：page 和 size 控制分页；status 和 type 为可选筛选条件，未传时不按对应字段筛选。
                    - 约束：当前操作者必须具备管理员身份。
                    - 处理：查询全部工单，并按创建时间倒序返回。
                    - 失败：当前操作者不是管理员 -> PermissionError.UNAUTHORIZED。
                    - 响应：返回工单分页数据。
                    """
    )
    @GetMapping("/listFeedback")
    public R<PageR<FeedbackEntity>> listFeedback(
            @RequestParam int page,
            @RequestParam int size,
            @RequestParam(required = false) FeedbackStatus status,
            @RequestParam(required = false) FeedbackType type) {
        return R.ok(feedbackService.listFeedback(page, size, status, type));
    }

    @Operation(
            summary = "获取用户工单详情",
            description = """
                    - 用途：供管理员查看指定用户工单的完整内容。
                    - 请求：feedbackId 指定目标工单。
                    - 约束：当前操作者必须具备管理员身份。
                    - 处理：按工单 ID 查询工单详情。
                    - 失败：当前操作者不是管理员 -> PermissionError.UNAUTHORIZED；工单不存在 -> SysError.FEEDBACK_NOT_FOUND。
                    - 响应：返回目标工单详情。
                    """
    )
    @GetMapping("/{feedbackId}")
    public R<FeedbackEntity> getFeedbackDetail(@PathVariable Long feedbackId) {
        return R.ok(feedbackService.getFeedbackDetail(feedbackId));
    }

    @Operation(
            summary = "更新用户工单状态",
            description = """
                    - 用途：供管理员推进用户工单的处理状态。
                    - 请求：feedbackId 指定目标工单；status 指定更新后的状态。
                    - 约束：当前操作者必须具备管理员身份；status 必须是 FeedbackStatus 定义的状态。
                    - 处理：更新目标工单状态；不修改工单内容和问题类型。
                    - 失败：当前操作者不是管理员 -> PermissionError.UNAUTHORIZED；工单不存在 -> SysError.FEEDBACK_NOT_FOUND。
                    - 响应：成功时返回空结果。
                    """
    )
    @PutMapping("/{feedbackId}/status")
    public R<Void> updateFeedbackStatus(
            @PathVariable Long feedbackId,
            @RequestParam FeedbackStatus status) {
        feedbackService.updateFeedbackStatus(feedbackId, status);
        return R.ok();
    }
}
