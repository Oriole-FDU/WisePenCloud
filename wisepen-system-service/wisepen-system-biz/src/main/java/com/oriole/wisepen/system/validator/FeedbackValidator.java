package com.oriole.wisepen.system.validator;

import com.oriole.wisepen.system.api.domain.dto.req.FeedbackSubmitRequest;
import com.oriole.wisepen.system.api.domain.dto.req.QuestionnaireSubmitRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 反馈及问卷数据验证器
 * 
 * 职责：
 * - 验证请求参数的合法性
 * - 检查业务规则的遵循情况
 * - 提供清晰的验证错误信息
 * 
 * @author Architecture Team
 */
@Slf4j
@Component
public class FeedbackValidator {
    
    /**
     * 验证简单反馈请求
     * 
     * @param request 反馈请求
     * @throws IllegalArgumentException 如果验证失败
     */
    public void validateFeedbackSubmitRequest(FeedbackSubmitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("反馈请求不能为空");
        }
        
        // 验证标题
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (request.getTitle().trim().length() > 100) {
            throw new IllegalArgumentException("标题长度不能超过 100 字");
        }
        
        // 验证内容
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("反馈内容不能为空");
        }
        String content = request.getContent().trim();
        if (content.length() < 10) {
            throw new IllegalArgumentException("反馈内容长度至少 10 字");
        }
        if (content.length() > 5000) {
            throw new IllegalArgumentException("反馈内容长度不能超过 5000 字");
        }
        
        // 验证反馈类型
        if (request.getFeedbackType() == null || request.getFeedbackType().trim().isEmpty()) {
            throw new IllegalArgumentException("反馈类型不能为空");
        }
        // TODO: 检查反馈类型是否为有效的枚举值
        // 可以通过 FeedbackType.isValid(request.getFeedbackType()) 验证
        
        // 验证联系方式（可选，但如果提供则要格式正确）
        if (request.getContact() != null && !request.getContact().trim().isEmpty()) {
            String contact = request.getContact().trim();
            if (contact.length() > 200) {
                throw new IllegalArgumentException("联系方式长度不能超过 200 字");
            }
            // TODO: 简单格式检查（邮箱/电话等）
        }
        
        // 验证优先级（可选，但如果提供则范围应在 1-5）
        if (request.getPriority() != null) {
            if (request.getPriority() < 1 || request.getPriority() > 5) {
                throw new IllegalArgumentException("优先级范围应在 1-5 之间");
            }
        }
    }
    
    /**
     * 验证问卷反馈请求
     * 
     * @param request 问卷反馈请求
     * @throws IllegalArgumentException 如果验证失败
     */
    public void validateQuestionnaireSubmitRequest(QuestionnaireSubmitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("问卷反馈请求不能为空");
        }
        
        // 验证问卷 ID
        if (request.getQuestionnaireId() == null || request.getQuestionnaireId() <= 0) {
            throw new IllegalArgumentException("问卷 ID 不能为空");
        }
        
        // 验证反馈类型
        if (request.getFeedbackType() == null || request.getFeedbackType().trim().isEmpty()) {
            throw new IllegalArgumentException("反馈类型不能为空");
        }
        
        // 验证答案列表
        if (request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new IllegalArgumentException("至少需要提供一个问题的答案");
        }
        
        // TODO: 验证每个答案的合法性（类型匹配、必填项、选项有效等）
    }
}
