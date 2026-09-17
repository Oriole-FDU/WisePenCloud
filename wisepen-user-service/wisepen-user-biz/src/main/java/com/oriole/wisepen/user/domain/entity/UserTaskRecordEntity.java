package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oriole.wisepen.user.api.enums.RewardType;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user_task_record")
public class UserTaskRecordEntity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private UserTaskCode taskCode;

    private RewardType rewardType;

    private Integer rewardAmount;

    private String walletTraceId;

    private String meta;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime completeTime;
}
