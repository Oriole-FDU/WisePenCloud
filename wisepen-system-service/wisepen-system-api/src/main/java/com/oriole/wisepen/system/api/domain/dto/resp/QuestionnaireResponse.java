package com.oriole.wisepen.system.api.domain.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 问卷响应
 * 
 * @author Architecture Team
 */
@Data
public class QuestionnaireResponse {
    
    private Long id;
    private String title;
    private String description;
    private String feedbackType;
    
    private List<QuestionResponse> questions;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
