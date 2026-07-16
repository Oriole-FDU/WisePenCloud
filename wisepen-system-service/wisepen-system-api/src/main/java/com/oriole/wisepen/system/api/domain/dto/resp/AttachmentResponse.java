package com.oriole.wisepen.system.api.domain.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 附件响应
 * 
 * @author Architecture Team
 */
@Data
public class AttachmentResponse {
    
    private Long id;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadedAt;
}
