package com.oriole.wisepen.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackSubmitRequest;
import com.oriole.wisepen.system.api.domain.dto.req.QuestionnaireSubmitRequest;
import com.oriole.wisepen.system.api.domain.dto.resp.FeedbackResponse;
import com.oriole.wisepen.system.api.domain.dto.resp.FeedbackDetailResponse;

/**
 * 反馈服务接口
 * 
 * @author Architecture Team
 */
public interface FeedbackService {
    
    /**
     * 提交简单反馈
     * 
     * @param userId 当前用户 ID
     * @param request 反馈请求
     * @return 反馈响应（包含反馈 ID）
     */
    FeedbackResponse submitFeedback(Long userId, FeedbackSubmitRequest request);
    
    /**
     * 通过问卷提交反馈
     * 
     * @param userId 当前用户 ID
     * @param request 问卷反馈请求
     * @return 反馈响应
     */
    FeedbackResponse submitQuestionnaireFeedback(Long userId, QuestionnaireSubmitRequest request);
    
    /**
     * 查询用户个人反馈列表
     * 
     * @param userId 用户 ID
     * @param page 分页页码（从 1 开始）
     * @param size 每页大小
     * @param status 反馈状态过滤（可选）
     * @return 分页结果
     */
    Page<FeedbackResponse> getUserFeedbacks(Long userId, int page, int size, String status);
    
    /**
     * 查询反馈详情
     * 
     * @param feedbackId 反馈 ID
     * @param userId 当前用户 ID（用于权限校验）
     * @return 反馈详情
     */
    FeedbackDetailResponse getFeedbackDetail(Long feedbackId, Long userId);
    
    /**
     * 更新反馈状态（管理员操作）
     * 
     * @param feedbackId 反馈 ID
     * @param newStatus 新状态
     * @param reason 变更原因
     * @param operatedById 操作者 ID
     */
    void updateFeedbackStatus(Long feedbackId, String newStatus, String reason, Long operatedById);
    
    /**
     * 指派反馈给处理人
     * 
     * @param feedbackId 反馈 ID
     * @param assignedToId 指派给的用户 ID
     * @param operatedById 操作者 ID
     */
    void assignFeedback(Long feedbackId, Long assignedToId, Long operatedById);
}
