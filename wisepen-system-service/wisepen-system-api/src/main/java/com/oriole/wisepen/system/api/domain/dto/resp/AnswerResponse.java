package com.oriole.wisepen.system.api.domain.dto.resp;

import lombok.Data;

/**
 * 问卷回答响应
 * 
 * @author Architecture Team
 */
@Data
public class AnswerResponse {
    
    private Long questionId;
    private String questionTitle;
    private String answerType;
    private String answerValue;
}
