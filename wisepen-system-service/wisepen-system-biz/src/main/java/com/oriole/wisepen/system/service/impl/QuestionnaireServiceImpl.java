package com.oriole.wisepen.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oriole.wisepen.system.api.domain.dto.resp.QuestionOptionResponse;
import com.oriole.wisepen.system.api.domain.dto.resp.QuestionResponse;
import com.oriole.wisepen.system.api.domain.dto.resp.QuestionnaireResponse;
import com.oriole.wisepen.system.domain.entity.QuestionnaireEntity;
import com.oriole.wisepen.system.domain.entity.QuestionnaireQuestionEntity;
import com.oriole.wisepen.system.mapper.QuestionnaireMapper;
import com.oriole.wisepen.system.mapper.QuestionnaireQuestionMapper;
import com.oriole.wisepen.system.service.QuestionnaireService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 问卷服务实现
 * 
 * @author Architecture Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireServiceImpl implements QuestionnaireService {
    
    private final QuestionnaireMapper questionnaireMapper;
    private final QuestionnaireQuestionMapper questionnaireQuestionMapper;
    
    @Override
    public List<QuestionnaireResponse> listByFeedbackType(String feedbackType) {
        List<QuestionnaireEntity> entities = questionnaireMapper.selectByFeedbackType(feedbackType);
        return entities.stream()
                .map(this::buildQuestionnaireResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public QuestionnaireResponse getDefaultQuestionnaire(String feedbackType) {
        QuestionnaireEntity entity = questionnaireMapper.selectDefaultByFeedbackType(feedbackType);
        if (entity == null) {
            log.warn("未找到类型 {} 的默认问卷", feedbackType);
            return null;
        }
        return buildQuestionnaireResponse(entity);
    }
    
    @Override
    public QuestionnaireResponse getQuestionnaireById(Long questionnaireId) {
        QuestionnaireEntity entity = questionnaireMapper.selectById(questionnaireId);
        if (entity == null) {
            log.warn("问卷不存在: {}", questionnaireId);
            return null;
        }
        return buildQuestionnaireResponse(entity);
    }
    
    /**
     * 将问卷实体转换为响应对象（包含问题列表）
     */
    private QuestionnaireResponse buildQuestionnaireResponse(QuestionnaireEntity entity) {
        QuestionnaireResponse response = new QuestionnaireResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setDescription(entity.getDescription());
        response.setFeedbackType(entity.getFeedbackType());
        response.setCreatedAt(entity.getCreateTime());
        
        // 加载问题列表
        List<QuestionnaireQuestionEntity> questions = 
            questionnaireQuestionMapper.selectByQuestionnaireId(entity.getId());
        response.setQuestions(questions.stream()
                .map(this::buildQuestionResponse)
                .collect(Collectors.toList()));
        
        return response;
    }
    
    /**
     * 将问题实体转换为响应对象
     */
    private QuestionResponse buildQuestionResponse(QuestionnaireQuestionEntity entity) {
        QuestionResponse response = new QuestionResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setDescription(entity.getDescription());
        response.setHelpText(entity.getHelpText());
        response.setQuestionType(entity.getQuestionType());
        response.setRequired(entity.getRequired() == 1);
        response.setQuestionOrder(entity.getQuestionOrder());
        
        // 解析选项 JSON（仅用于 SELECT/RADIO/CHECKBOX）
        if ("SELECT".equals(entity.getQuestionType()) || 
            "RADIO".equals(entity.getQuestionType()) || 
            "CHECKBOX".equals(entity.getQuestionType())) {
            response.setOptions(parseOptions(entity.getOptions()));
        }
        
        return response;
    }
    
    /**
     * 解析选项 JSON 字符串
     * TODO: 实际使用时需依赖 JSON 解析库（如 Jackson/Gson）
     */
    private List<QuestionOptionResponse> parseOptions(String optionsJson) {
        // 暂时返回空列表，实际需要 JSON 解析
        return new ArrayList<>();
    }
}
