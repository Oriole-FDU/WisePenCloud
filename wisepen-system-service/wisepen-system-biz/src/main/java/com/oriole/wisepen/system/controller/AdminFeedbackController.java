package com.oriole.wisepen.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.system.api.domain.dto.resp.FeedbackResponse;
import com.oriole.wisepen.system.service.FeedbackService;
import com.oriole.wisepen.system.service.FeedbackStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员反馈管理 Controller
 * 
 * 功能：
 * - 查询、筛选、分页反馈列表
 * - 更新反馈状态
 * - 指派反馈给处理人
 * 
 * @author Architecture Team
 */
@Slf4j
@Tag(name = "管理员反馈管理", description = "反馈处理、指派、状态管理")
@RestController
@RequestMapping("/admin/feedback")
@RequiredArgsConstructor
public class AdminFeedbackController {
    
    private final FeedbackService feedbackService;
    private final FeedbackStatusService feedbackStatusService;
    
    /**
     * 查询反馈列表（支持分页、筛选、排序）
     * 
     * TODO: 实现完整的搜索、筛选、排序功能
     * 当前返回空响应，实际应支持：
     * - 按状态、反馈类型、优先级、创建时间筛选
     * - 按优先级、创建时间排序
     * - 返回分页结果
     */
    @Operation(
            summary = "查询反馈列表",
            description = """
                    - 用途：管理员查看所有反馈，支持筛选和排序。
                    - 请求：page（页码）、size（页面大小）、status（状态过滤）、type（反馈类型）、priority（优先级）。
                    - 约束：当前用户必须为管理员；分页参数必须合法。
                    - 响应：返回反馈分页列表。
                    """
    )
    @CheckLogin  // TODO: 需要额外的 @CheckRole("ADMIN") 或等效权限检查
    @GetMapping("/list")
    public R<Page<FeedbackResponse>> listFeedbacks(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "状态过滤（可选）") @RequestParam(required = false) String status,
            @Parameter(description = "反馈类型过滤（可选）") @RequestParam(required = false) String type,
            @Parameter(description = "优先级过滤（可选）") @RequestParam(required = false) Integer priority) {
        
        // TODO: 调用 FeedbackMapper.selectPage() 或类似方法实现查询
        // 当前为占位实现
        Page<FeedbackResponse> page_result = new Page<>();
        return R.ok(page_result);
    }
    
    /**
     * 更新反馈状态
     * 
     * 状态流转规则已在 FeedbackStatusServiceImpl 中定义
     */
    @Operation(
            summary = "更新反馈状态",
            description = """
                    - 用途：管理员更改反馈的处理状态。
                    - 请求：feedbackId（反馈 ID）、newStatus（目标状态）、reason（变更原因）。
                    - 约束：当前用户必须为管理员；状态流转必须合法。
                    - 处理：更新反馈状态，记录状态历史。
                    - 失败：反馈不存在、状态流转非法。
                    - 响应：成功返回空结果。
                    """
    )
    @CheckLogin  // TODO: 需要额外的 @CheckRole("ADMIN") 或等效权限检查
    @PutMapping("/{feedbackId}/status")
    public R<Void> updateFeedbackStatus(
            @Parameter(description = "反馈 ID") @PathVariable Long feedbackId,
            @Parameter(description = "目标状态") @RequestParam String newStatus,
            @Parameter(description = "变更原因") @RequestParam(required = false) String reason) {
        try {
            Long operatedById = SecurityContextHolder.getUserId();
            feedbackService.updateFeedbackStatus(feedbackId, newStatus, reason, operatedById);
            log.info("反馈状态已更新: feedbackId={}, newStatus={}, operatedBy={}", 
                feedbackId, newStatus, operatedById);
            return R.ok();
        } catch (IllegalArgumentException e) {
            log.warn("状态更新失败: {}", e.getMessage());
            return R.fail(e.getMessage());
        }
    }
    
    /**
     * 指派反馈给处理人
     */
    @Operation(
            summary = "指派反馈给处理人",
            description = """
                    - 用途：管理员将反馈分配给具体的处理人。
                    - 请求：feedbackId（反馈 ID）、assignedToId（处理人 ID）。
                    - 约束：当前用户必须为管理员；反馈必须存在。
                    - 处理：更新反馈的 assignedToId，记录操作历史。
                    - 响应：成功返回空结果。
                    """
    )
    @CheckLogin  // TODO: 需要额外的 @CheckRole("ADMIN") 或等效权限检查
    @PutMapping("/{feedbackId}/assign")
    public R<Void> assignFeedback(
            @Parameter(description = "反馈 ID") @PathVariable Long feedbackId,
            @Parameter(description = "处理人 ID") @RequestParam Long assignedToId) {
        try {
            Long operatedById = SecurityContextHolder.getUserId();
            feedbackService.assignFeedback(feedbackId, assignedToId, operatedById);
            log.info("反馈已指派: feedbackId={}, assignedToId={}, operatedBy={}", 
                feedbackId, assignedToId, operatedById);
            return R.ok();
        } catch (IllegalArgumentException e) {
            log.warn("指派失败: {}", e.getMessage());
            return R.fail(e.getMessage());
        }
    }
    
    /**
     * 获取某个状态允许的后续状态列表
     * 
     * 用于 UI 显示"下一步操作"的可用选项
     */
    @Operation(
            summary = "获取允许的后续状态",
            description = """
                    - 用途：查询某个状态可以流转到哪些后续状态（用于 UI 显示操作选项）。
                    - 请求：currentStatus（当前状态）。
                    - 响应：返回允许的后续状态列表。
                    """
    )
    @GetMapping("/status-transitions")
    public R<Map<String, Object>> getAllowedNextStatuses(
            @Parameter(description = "当前状态") @RequestParam String currentStatus) {
        List<String> nextStatuses = feedbackStatusService.getAllowedNextStatuses(currentStatus);
        Map<String, Object> result = new HashMap<>();
        result.put("currentStatus", currentStatus);
        result.put("allowedNextStatuses", nextStatuses);
        return R.ok(result);
    }
}
