package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_group_announcement")
public class GroupAnnouncementEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long announcementId;

    private Long groupId;
    private Long publisherId;
    private String content;

    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
