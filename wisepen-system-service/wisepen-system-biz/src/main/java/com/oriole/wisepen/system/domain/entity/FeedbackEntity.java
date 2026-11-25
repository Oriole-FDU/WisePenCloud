package com.oriole.wisepen.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.oriole.wisepen.system.api.enums.FeedbackStatus;
import com.oriole.wisepen.system.api.enums.FeedbackType;
import com.oriole.wisepen.system.mapper.handler.FeedbackTypeSetTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@TableName(value = "feedback", autoResultMap = true)
public class FeedbackEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String content;
    private String contact;
    @TableField("image_url")
    private String imageUrl;
    private String browser;
    @TableField(value = "type", typeHandler = FeedbackTypeSetTypeHandler.class)
    private Set<FeedbackType> type;
    private FeedbackStatus status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
