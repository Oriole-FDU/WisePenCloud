package com.oriole.wisepen.system.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oriole.wisepen.system.api.enums.BatchTaskItemStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@TableName("batch_task_item")
public class BatchTaskItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private BatchTaskItemStatus status;
    private String meta;
    private String result;
    private Integer errorCode;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
