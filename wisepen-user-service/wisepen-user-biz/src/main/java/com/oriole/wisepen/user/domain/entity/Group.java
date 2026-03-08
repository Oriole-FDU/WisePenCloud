package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_group")
public class Group implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 组ID (雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 组名 */
    private String name;

    /** 组长/负责人ID */
    private Long ownerId;

    /** 描述 */
    private String description;

    private Integer type;

    private String coverUrl;

    private String inviteCode;

    /** 逻辑删除 0:未删 1:已删 */
    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;

    private Integer memberCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}