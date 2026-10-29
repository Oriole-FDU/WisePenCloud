package com.oriole.wisepen.system.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oriole.wisepen.system.api.enums.BatchTaskStatus;
import com.oriole.wisepen.system.api.enums.BatchTaskType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@TableName("batch_task")
public class BatchTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private BatchTaskType type;
    private String name;
    private BatchTaskStatus status;
    private String meta;
    private Long operatorUserId;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private LocalDateTime deletedAt;
    private Long deletedBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
