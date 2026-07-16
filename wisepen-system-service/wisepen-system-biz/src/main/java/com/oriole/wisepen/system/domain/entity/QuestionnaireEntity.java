package com.oriole.wisepen.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问卷模板实体
 * 
 * @author Architecture Team
 */
@Data
@TableName("questionnaire")
public class QuestionnaireEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String title;
    private String description;
    
    private String feedbackType;
    private Integer isActive;
    private Integer isPublic;
    
    private Integer questionCount;
    private Integer requireAttachments;
    private Long maxAttachmentSize;
    
    private Long createdById;
    
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
