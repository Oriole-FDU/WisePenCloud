package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author Administrator
 */
@Data
@TableName("sys_group_member_quotas")
public class GroupMemberQuotas implements Serializable {
	@TableId(type = IdType.INPUT) // 关键：id不是自增，必须手动填入 member.id
	private Long id;

	private Integer quotaUsed;

	private Integer quotaLimit;

	private LocalDateTime updateTime;
}
