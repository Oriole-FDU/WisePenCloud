package com.oriole.wisepen.system.api.domain.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 通过问卷提交反馈请求
 * 
 * @author Architecture Team
 */
@Data
public class QuestionnaireSubmitRequest {
    
    /**
     * 问卷 ID
     */
    private Long questionnaireId;
    
    /**
     * 反馈标题（可选）
     */
    private String title;
    
    /**
     * 反馈类型（必填）
     */
    private String feedbackType;
    
    /**
     * 联系方式（可选）
     */
    private String contact;
    
    /**
     * 优先级（可选，默认 2-中）
     */
    private Integer priority;
    
    /**
     * 问卷回答列表
     */
    private List<AnswerRequest> answers;
    
    /**
     * 已上传附件 ID 列表
     */
    private List<String> attachmentIds;
}
