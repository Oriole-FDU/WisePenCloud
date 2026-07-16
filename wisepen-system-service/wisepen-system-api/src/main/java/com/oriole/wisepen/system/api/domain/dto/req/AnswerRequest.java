package com.oriole.wisepen.system.api.domain.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 问卷问题回答请求
 * 
 * @author Architecture Team
 */
@Data
public class AnswerRequest {
    
    /**
     * 问题 ID
     */
    private Long questionId;
    
    /**
     * 回答类型（与问题类型一致）
     */
    private String answerType;
    
    /**
     * 单值回答（TEXT, TEXTAREA, SELECT, RADIO）
     */
    private String answerValue;
    
    /**
     * 多值回答（CHECKBOX）
     */
    private List<String> answerList;
}
