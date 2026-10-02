package com.oriole.wisepen.system.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oriole.wisepen.system.api.enums.FeedbackStatus;
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
    private FeedbackStatus status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
