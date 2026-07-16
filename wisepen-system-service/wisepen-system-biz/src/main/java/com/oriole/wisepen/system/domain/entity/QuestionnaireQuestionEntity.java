package com.oriole.wisepen.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问卷问题实体
 * 
 * @author Architecture Team
 */
@Data
@TableName("questionnaire_question")
public class QuestionnaireQuestionEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long questionnaireId;
    
    private String title;
    private String description;
    private String helpText;
    
    private String questionType;
    private Integer required;
    
    private String options;
    
    private Integer questionOrder;
    
    private String validationRules;
    
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
