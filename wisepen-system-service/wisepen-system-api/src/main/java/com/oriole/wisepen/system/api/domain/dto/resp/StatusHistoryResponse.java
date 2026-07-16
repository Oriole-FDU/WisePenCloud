package com.oriole.wisepen.system.api.domain.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 状态历史响应
 * 
 * @author Architecture Team
 */
@Data
public class StatusHistoryResponse {
    
    private Long id;
    private String oldStatus;
    private String newStatus;
    private String operationType;
    private String operatedBy;
    private String reason;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
