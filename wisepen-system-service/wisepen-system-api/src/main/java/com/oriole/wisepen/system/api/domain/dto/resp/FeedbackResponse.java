package com.oriole.wisepen.system.api.domain.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 反馈响应 DTO
 * 
 * @author Architecture Team
 */
@Data
public class FeedbackResponse {
    
    private Long id;
    private String title;
    private String feedbackType;
    private String status;
    private Integer priority;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
