package com.oriole.wisepen.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈回答实体
 * 
 * @author Architecture Team
 */
@Data
@TableName("feedback_answer")
public class FeedbackAnswerEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long feedbackId;
    private Long questionId;
    
    private String answerType;
    private String answerValue;
    private String answerJson;
    
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
