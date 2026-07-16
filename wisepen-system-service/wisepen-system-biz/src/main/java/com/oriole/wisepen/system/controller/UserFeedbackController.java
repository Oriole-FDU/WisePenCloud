package com.oriole.wisepen.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackRequest;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackSubmitRequest;
import com.oriole.wisepen.system.api.domain.dto.req.QuestionnaireSubmitRequest;
import com.oriole.wisepen.system.api.domain.dto.resp.FeedbackResponse;
import com.oriole.wisepen.system.api.domain.dto.resp.FeedbackDetailResponse;
import com.oriole.wisepen.system.api.domain.dto.resp.QuestionnaireResponse;
import com.oriole.wisepen.system.service.FeedbackService;
import com.oriole.wisepen.system.service.QuestionnaireService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;


@Slf4j
@Tag(name = "用户反馈", description = "用户提交问题报错、功能建议与使用咨询")
@RestController
@RequestMapping("/system/feedback")
@RequiredArgsConstructor
public class UserFeedbackController {

    private final FeedbackService feedbackService;
    private final QuestionnaireService questionnaireService;

    /**
     * 【保留】原有的简单反馈接口（向后兼容）
     */
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
    
    /**
     * 【新增】简单反馈提交（新 API 格式）
     */
    @Operation(
            summary = "提交简单反馈",
            description = """
                    - 用途：让登录用户提交简单反馈，不使用问卷。
                    - 请求：title（标题）、content（正文）、feedbackType（反馈类型）、contact（联系方式）、priority（优先级）。
                    - 约束：content 长度 >= 10 字；feedbackType 为有效的反馈类型。
                    - 处理：创建反馈，初始状态为 PENDING，创建状态历史记录。
                    - 响应：返回创建的反馈摘要信息。
                    """
    )
    @CheckLogin
    @PostMapping("/submit")
    public R<FeedbackResponse> submitFeedback(@Valid @RequestBody FeedbackSubmitRequest request) {
        try {
            Long userId = SecurityContextHolder.getUserId();
            FeedbackResponse response = feedbackService.submitFeedback(userId, request);
            return R.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("反馈提交失败: {}", e.getMessage());
            return R.fail(e.getMessage());
        }
    }
    
    /**
     * 【新增】获取问卷模板列表
     */
    @Operation(
            summary = "获取问卷模板列表",
            description = """
                    - 用途：列举某个反馈类型的所有问卷模板供用户选择。
                    - 请求：feedbackType（反馈类型）。
                    - 响应：返回问卷列表，包含问卷 ID、标题、描述。
                    """
    )
    @GetMapping("/questionnaires/{feedbackType}")
    public R<List<QuestionnaireResponse>> listQuestionnaires(
            @Parameter(description = "反馈类型") @PathVariable String feedbackType) {
        List<QuestionnaireResponse> questionnaires = questionnaireService.listByFeedbackType(feedbackType);
        return R.ok(questionnaires);
    }
    
    /**
     * 【新增】获取单个问卷详情
     */
    @Operation(
            summary = "获取问卷详情",
            description = """
                    - 用途：获取问卷的完整信息，包含所有问题及其选项。
                    - 请求：questionnaireId（问卷 ID）。
                    - 响应：返回问卷详情及问题列表。
                    """
    )
    @GetMapping("/questionnaires/{questionnaireId}/detail")
    public R<QuestionnaireResponse> getQuestionnaire(
            @Parameter(description = "问卷 ID") @PathVariable Long questionnaireId) {
        QuestionnaireResponse questionnaire = questionnaireService.getQuestionnaireById(questionnaireId);
        if (questionnaire == null) {
            return R.fail("问卷不存在");
        }
        return R.ok(questionnaire);
    }
    
    /**
     * 【新增】通过问卷提交反馈
     */
    @Operation(
            summary = "通过问卷提交反馈",
            description = """
                    - 用途：用户选择问卷后，回答问卷问题并提交反馈。
                    - 请求：questionnaireId（问卷 ID）、feedbackType（反馈类型）、answers（问题回答列表）。
                    - 约束：问卷必须存在；所有必填问题必须有答案；答案必须符合问题类型。
                    - 处理：创建反馈，保存所有问卷回答，初始状态为 PENDING。
                    - 响应：返回创建的反馈摘要信息。
                    """
    )
    @CheckLogin
    @PostMapping("/submit-questionnaire")
    public R<FeedbackResponse> submitQuestionnaireFeedback(@Valid @RequestBody QuestionnaireSubmitRequest request) {
        try {
            Long userId = SecurityContextHolder.getUserId();
            FeedbackResponse response = feedbackService.submitQuestionnaireFeedback(userId, request);
            return R.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("问卷反馈提交失败: {}", e.getMessage());
            return R.fail(e.getMessage());
        }
    }
    
    /**
     * 【新增】获取用户的反馈列表
     */
    @Operation(
            summary = "获取我的反馈列表",
            description = """
                    - 用途：登录用户查看自己提交的所有反馈。
                    - 请求：page（页码）、size（页面大小）、status（可选的状态过滤）。
                    - 约束：当前用户必须已登录；分页参数必须合法。
                    - 响应：返回反馈分页列表（不包含详细答案和附件）。
                    """
    )
    @CheckLogin
    @GetMapping("/my-feedbacks")
    public R<Page<FeedbackResponse>> getMyFeedbacks(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "状态过滤（可选）") @RequestParam(required = false) String status) {
        Long userId = SecurityContextHolder.getUserId();
        Page<FeedbackResponse> result = feedbackService.getUserFeedbacks(userId, page, size, status);
        return R.ok(result);
    }
    
    /**
     * 【新增】获取反馈详情
     */
    @Operation(
            summary = "获取反馈详情",
            description = """
                    - 用途：用户查看自己提交的反馈的完整信息，包括问卷回答、附件、状态历史。
                    - 请求：feedbackId（反馈 ID）。
                    - 约束：当前用户必须已登录；用户只能查看自己的反馈。
                    - 失败：反馈不存在 -> NOT_FOUND；无权限 -> PERMISSION_DENIED。
                    - 响应：返回反馈详情及所有附属信息。
                    """
    )
    @CheckLogin
    @GetMapping("/{feedbackId}")
    public R<FeedbackDetailResponse> getFeedbackDetail(
            @Parameter(description = "反馈 ID") @PathVariable Long feedbackId) {
        try {
            Long userId = SecurityContextHolder.getUserId();
            FeedbackDetailResponse response = feedbackService.getFeedbackDetail(feedbackId, userId);
            return R.ok(response);
        } catch (SecurityException e) {
            log.warn("无权限查看反馈: feedbackId={}, userId={}", feedbackId, SecurityContextHolder.getUserId());
            return R.fail("无权限查看此反馈");
        } catch (IllegalArgumentException e) {
            log.warn("反馈查询失败: {}", e.getMessage());
            return R.fail(e.getMessage());
        }
    }
}
