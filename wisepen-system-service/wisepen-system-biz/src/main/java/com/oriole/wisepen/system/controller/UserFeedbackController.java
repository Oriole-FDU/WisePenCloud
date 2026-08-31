package com.oriole.wisepen.system.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackRequest;
import com.oriole.wisepen.system.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "用户反馈", description = "用户提交问题报错、功能建议与使用咨询")
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
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；反馈记录写入发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：成功时返回空结果。
                    """
    )
    @CheckLogin
    @PostMapping("/addFeedback")
    public R<Void> createFeedback(@Validated @RequestBody FeedbackRequest feedbackRequest) {
        feedbackService.createFeedback(SecurityContextHolder.getUserId(), feedbackRequest);
        return R.ok();
    }
}
