package com.oriole.wisepen.system.controller;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackQueryRequest;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackStatusChangeRequest;
import com.oriole.wisepen.system.api.domain.dto.res.FeedbackDetailResponse;
import com.oriole.wisepen.system.api.domain.dto.res.FeedbackListItemResponse;
import com.oriole.wisepen.system.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理员 - 用户反馈", description = "管理员查看用户反馈并修改处理状态")
@RestController
@RequestMapping("/system/admin/feedback")
@RequiredArgsConstructor
@CheckRole(IdentityType.ADMIN)
@Validated
public class AdminFeedbackController {
    private final FeedbackService feedbackService;

    @Operation(
            summary = "分页查询用户反馈",
            description = """
                    - 用途：供管理员筛选用户反馈并查看当前处理状态。
                    - 请求：type 为可选类型集合，可取 BUG_REPORT、SUGGESTION、CONSULTATION、COMPLAINT、OTHER，通过重复参数传入，如 type=BUG_REPORT&type=SUGGESTION，命中任一类型即可，不传或空集合时不限类型；status、userId 为可选精确条件；startTime 和 endTime 为创建时间的闭区间，使用 ISO 日期时间；page 默认 1，size 默认 20，最大 100。
                    - 约束：当前身份必须是平台管理员；开始时间不得晚于结束时间。
                    - 处理：按创建时间和 ID 倒序分页读取反馈元信息。
                    - 失败：当前身份不是管理员 -> PermissionError.UNAUTHORIZED。
                    - 响应：返回反馈分页，包含反馈 ID、提交用户 ID、type 类型数组、状态和创建及更新时间；完整内容通过详情接口获取。
                    """
    )
    @GetMapping("/listFeedbacks")
    public R<PageR<FeedbackListItemResponse>> listFeedbacks(@Valid @ModelAttribute @ParameterObject FeedbackQueryRequest request) {
        return R.ok(feedbackService.listFeedbacks(request));
    }

    @Operation(
            summary = "获取用户反馈详情",
            description = """
                    - 用途：供管理员打开一条反馈，查看用户提交的完整内容。
                    - 请求：feedbackId 指定反馈。
                    - 约束：当前身份必须是平台管理员；反馈必须存在。
                    - 处理：按 ID 读取反馈；图片地址按提交时保存的值返回。
                    - 失败：反馈不存在 -> SysError.FEEDBACK_NOT_FOUND；当前身份不是管理员 -> PermissionError.UNAUTHORIZED。
                    - 响应：返回反馈元信息及正文、联系方式、图片地址和浏览器信息。
                    """
    )
    @GetMapping("/getFeedbackDetail")
    public R<FeedbackDetailResponse> getFeedbackDetail(@RequestParam @Positive Long feedbackId) {
        return R.ok(feedbackService.getFeedbackDetail(feedbackId));
    }

    @Operation(
            summary = "修改反馈状态",
            description = """
                    - 用途：由管理员标记反馈的处理进度或结果。
                    - 请求：feedbackId 指定反馈；status 为目标状态，可取 PENDING、PROCESSING、RESOLVED、IGNORED、CLOSED。
                    - 约束：当前身份必须是平台管理员；反馈必须存在；允许在上述状态间直接修改。
                    - 处理：更新反馈状态和最后更新时间；设置为当前状态时直接成功，并发修改以最后写入为准。
                    - 失败：反馈不存在 -> SysError.FEEDBACK_NOT_FOUND；当前身份不是管理员 -> PermissionError.UNAUTHORIZED。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "修改反馈状态", businessType = BusinessType.UPDATE)
    @PostMapping("/changeStatus")
    public R<Void> changeStatus(@Valid @RequestBody FeedbackStatusChangeRequest request) {
        feedbackService.changeStatus(request);
        return R.ok();
    }
}
