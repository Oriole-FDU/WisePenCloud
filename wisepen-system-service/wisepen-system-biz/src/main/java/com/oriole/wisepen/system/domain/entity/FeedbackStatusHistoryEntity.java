package com.oriole.wisepen.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈状态历史实体
 * 
 * @author Architecture Team
 */
@Data
@TableName("feedback_status_history")
public class FeedbackStatusHistoryEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long feedbackId;
    
    private String oldStatus;
    private String newStatus;
    private String reason;
    
    private Long operatedById;
    private String operationType;
    
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
