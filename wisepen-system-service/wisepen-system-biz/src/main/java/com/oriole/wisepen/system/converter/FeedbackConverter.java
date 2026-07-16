package com.oriole.wisepen.system.converter;

import com.oriole.wisepen.system.api.domain.dto.resp.FeedbackResponse;
import com.oriole.wisepen.system.api.domain.dto.resp.QuestionnaireResponse;
import com.oriole.wisepen.system.api.domain.dto.resp.QuestionResponse;
import com.oriole.wisepen.system.domain.entity.FeedbackEntity;
import com.oriole.wisepen.system.domain.entity.QuestionnaireEntity;
import com.oriole.wisepen.system.domain.entity.QuestionnaireQuestionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 反馈及问卷数据转换器
 * 
 * 职责：
 * - Entity ↔ DTO 双向转换
 * - 复杂对象的嵌套转换
 * - 保持转换逻辑集中和可复用
 * 
 * @author Architecture Team
 */
@Component
@RequiredArgsConstructor
public class FeedbackConverter {
    
    /**
     * FeedbackEntity → FeedbackResponse 转换
     */
    public FeedbackResponse toFeedbackResponse(FeedbackEntity entity) {
        if (entity == null) {
            return null;
        }
        
        FeedbackResponse response = new FeedbackResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setFeedbackType(entity.getFeedbackType());
        response.setStatus(entity.getStatus());
        response.setPriority(entity.getPriority());
        response.setCreatedAt(entity.getCreateTime());
        response.setUpdatedAt(entity.getUpdateTime());
        
        return response;
    }
    
    /**
     * List<FeedbackEntity> → List<FeedbackResponse> 批量转换
     */
    public List<FeedbackResponse> toFeedbackResponseList(List<FeedbackEntity> entities) {
        if (entities == null) {
            return new ArrayList<>();
        }
        return entities.stream()
                .map(this::toFeedbackResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * QuestionnaireEntity → QuestionnaireResponse 转换
     */
    public QuestionnaireResponse toQuestionnaireResponse(QuestionnaireEntity entity) {
        if (entity == null) {
            return null;
        }
        
        QuestionnaireResponse response = new QuestionnaireResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setDescription(entity.getDescription());
        response.setFeedbackType(entity.getFeedbackType());
        response.setCreatedAt(entity.getCreateTime());
        
        return response;
    }
    
    /**
     * QuestionnaireQuestionEntity → QuestionResponse 转换
     */
    public QuestionResponse toQuestionResponse(QuestionnaireQuestionEntity entity) {
        if (entity == null) {
            return null;
        }
        
        QuestionResponse response = new QuestionResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setDescription(entity.getDescription());
        response.setHelpText(entity.getHelpText());
        response.setQuestionType(entity.getQuestionType());
        response.setRequired(entity.getRequired() == 1);
        response.setQuestionOrder(entity.getQuestionOrder());
        
        return response;
    }
    
    /**
     * List<QuestionnaireQuestionEntity> → List<QuestionResponse> 批量转换
     */
    public List<QuestionResponse> toQuestionResponseList(List<QuestionnaireQuestionEntity> entities) {
        if (entities == null) {
            return new ArrayList<>();
        }
        return entities.stream()
                .map(this::toQuestionResponse)
                .collect(Collectors.toList());
    }
}
