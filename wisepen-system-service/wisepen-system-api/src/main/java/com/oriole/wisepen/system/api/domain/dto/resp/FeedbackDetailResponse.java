package com.oriole.wisepen.system.api.domain.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 反馈详情响应
 * 
 * @author Architecture Team
 */
@Data
public class FeedbackDetailResponse {
    
    private Long id;
    private String title;
    private String content;
    private String feedbackType;
    private String status;
    private Integer priority;
    private String contact;
    
    // 问卷关联
    private Long questionnaireId;
    private List<AnswerResponse> answers;
    
    // 附件
    private List<AttachmentResponse> attachments;
    
    // 状态历史
    private List<StatusHistoryResponse> statusHistory;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
