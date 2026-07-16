package com.oriole.wisepen.system.api.domain.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 提交简单反馈请求
 * 
 * @author Architecture Team
 */
@Data
public class FeedbackSubmitRequest {
    
    /**
     * 反馈标题（可选）
     */
    private String title;
    
    /**
     * 反馈内容（必填，最少 10 字）
     */
    private String content;
    
    /**
     * 反馈类型（必填）: BUG_REPORT, SUGGESTION, CONSULTATION, COMPLAINT, OTHER
     */
    private String feedbackType;
    
    /**
     * 联系方式（可选）
     */
    private String contact;
    
    /**
     * 优先级（可选，默认 2-中）: 1-高, 2-中, 3-低
     */
    private Integer priority;
    
    /**
     * 已上传附件 ID 列表（可选，先上传再关联）
     */
    private List<String> attachmentIds;
}
