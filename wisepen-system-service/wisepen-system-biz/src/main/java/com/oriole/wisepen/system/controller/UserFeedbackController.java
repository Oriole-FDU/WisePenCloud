package com.oriole.wisepen.system.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackRequest;
import com.oriole.wisepen.system.api.enums.FeedbackStatus;
import com.oriole.wisepen.system.api.enums.FeedbackType;
import com.oriole.wisepen.system.domain.entity.FeedbackEntity;
import com.oriole.wisepen.system.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户反馈", description = "用户提交问题报错、功能建议与使用咨询")
@Validated
@RestController
@RequestMapping("/system/feedback")
@RequiredArgsConstructor
public class UserFeedbackController {

    private final FeedbackService feedbackService;

    @Operation(
            summary = "提交用户反馈",
            description = """
                    - 用途：让登录用户提交问题报错、功能建议或使用咨询反馈。
                    - 请求：content 为反馈正文；contact 为联系方式；imageUrl 为可选图片地址；bugReport、suggestion、consultation、complaint 和 other 标记反馈包含的问题类型。
                    - 约束：当前用户必须已登录；content 必须表达有效的反馈内容；至少一个问题类型标记必须为 true。
                    - 处理：创建反馈记录；不在本接口分派处理人或发送通知。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：成功时返回空结果。
                    """
    )
    @CheckLogin
    @PostMapping("/addFeedback")
    public R<Void> createFeedback(@Valid @RequestBody FeedbackRequest feedbackRequest) {
        feedbackService.createFeedback(SecurityContextHolder.getUserId(), feedbackRequest);
        return R.ok();
    }

    @Operation(
            summary = "分页查询我的工单",
            description = """
                    - 用途：供登录用户查看自己提交的工单。
                    - 请求：page 和 size 控制分页；status 和 type 为可选筛选条件。
                    - 约束：当前用户必须已登录；page 和 size 必须为正数。
                    - 处理：仅查询当前用户的工单，并按创建时间倒序返回。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：返回当前用户的工单分页数据。
                    """
    )
    @CheckLogin
    @GetMapping("/list")
    public R<PageR<FeedbackEntity>> pageMyFeedbacks(
            @Positive @RequestParam int page,
            @Positive @RequestParam int size,
            @RequestParam(required = false) FeedbackStatus status,
            @RequestParam(required = false) FeedbackType type) {
        return R.ok(feedbackService.pageMyFeedbacks(
                SecurityContextHolder.getUserId(), page, size, status, type));
    }

    @Operation(
            summary = "获取我的工单详情",
            description = """
                    - 用途：供登录用户查看自己提交的单个工单。
                    - 请求：feedbackId 指定目标工单。
                    - 约束：当前用户必须已登录且是工单提交人。
                    - 处理：按工单 ID 和当前用户 ID 查询工单详情。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；工单不存在或不属于当前用户 -> SysError.FEEDBACK_NOT_FOUND。
                    - 响应：返回目标工单详情。
                    """
    )
    @CheckLogin
    @GetMapping("/{feedbackId}")
    public R<FeedbackEntity> getMyFeedbackDetail(@PathVariable Long feedbackId) {
        return R.ok(feedbackService.getMyFeedbackDetail(SecurityContextHolder.getUserId(), feedbackId));
    }
}
