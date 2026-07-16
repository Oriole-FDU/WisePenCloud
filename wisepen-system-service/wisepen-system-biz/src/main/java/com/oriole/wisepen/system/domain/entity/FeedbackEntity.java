package com.oriole.wisepen.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Xiong Heng
 */
@Data
@TableName("feedback")
public class FeedbackEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    
    private String title;
    private String content;
    private String contact;
    private String imageUrl;
    
    // 反馈分类与优先级
    private String feedbackType;
    private Integer priority;
    
    // 状态追踪
    private String status;
    private Long assignedToId;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    
    // 问卷关联
    private Long questionnaireId;
    
    // 兼容旧版字段（deprecated，可选）
    private Boolean bugReport;
    private Boolean suggestion;
    private Boolean consultation;
    private Boolean complaint;
    private Boolean other;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
