package com.oriole.wisepen.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈问卷模板实体
 * 
 * @author Architecture Team
 */
@Data
@TableName("feedback_template")
public class FeedbackTemplateEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String templateName;
    private String description;
    
    private Long questionnaireId;
    private Integer isDefault;
    private Integer isArchived;
    
    private Integer version;
    private Long createdById;
    
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
