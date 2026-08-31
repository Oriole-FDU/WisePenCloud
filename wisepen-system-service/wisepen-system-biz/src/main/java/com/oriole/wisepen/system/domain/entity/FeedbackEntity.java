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
    private String content;
    private String contact;
    private String imageUrl;
    private Boolean bugReport;
    private Boolean suggestion;
    private Boolean consultation;
    private Boolean complaint;
    private Boolean other;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
