package com.oriole.wisepen.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈附件实体
 * 
 * @author Architecture Team
 */
@Data
@TableName("feedback_attachment")
public class FeedbackAttachmentEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long feedbackId;
    
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    
    private Long uploadedById;
    
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
