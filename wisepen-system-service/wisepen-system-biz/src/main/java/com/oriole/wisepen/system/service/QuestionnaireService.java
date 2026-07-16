package com.oriole.wisepen.system.service;

import com.oriole.wisepen.system.api.domain.dto.resp.QuestionnaireResponse;

import java.util.List;

/**
 * 问卷服务接口
 * 
 * @author Architecture Team
 */
public interface QuestionnaireService {
    
    /**
     * 按反馈类型获取问卷列表
     * 
     * @param feedbackType 反馈类型
     * @return 问卷列表
     */
    List<QuestionnaireResponse> listByFeedbackType(String feedbackType);
    
    /**
     * 获取某反馈类型的默认问卷
     * 
     * @param feedbackType 反馈类型
     * @return 问卷详情
     */
    QuestionnaireResponse getDefaultQuestionnaire(String feedbackType);
    
    /**
     * 按 ID 获取问卷（含问题列表）
     * 
     * @param questionnaireId 问卷 ID
     * @return 问卷详情
     */
    QuestionnaireResponse getQuestionnaireById(Long questionnaireId);
}
