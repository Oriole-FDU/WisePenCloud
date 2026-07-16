package com.oriole.wisepen.system.api.domain.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 问卷问题响应
 * 
 * @author Architecture Team
 */
@Data
public class QuestionResponse {
    
    private Long id;
    private String title;
    private String description;
    private String helpText;
    private String questionType;
    private Boolean required;
    private Integer questionOrder;
    
    /**
     * 选项列表（SELECT, RADIO, CHECKBOX 类型使用）
     */
    private List<QuestionOptionResponse> options;
}
