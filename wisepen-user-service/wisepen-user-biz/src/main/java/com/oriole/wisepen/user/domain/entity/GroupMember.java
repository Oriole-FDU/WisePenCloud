package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_group_member")
public class GroupMember implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 组ID */
    private Long groupId;

    /** 用户ID */
    private Long userId;

    /** 用户角色 */
    private GroupRoleType role;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinTime;
}