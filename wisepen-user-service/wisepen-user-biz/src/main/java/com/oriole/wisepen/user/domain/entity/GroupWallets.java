package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * @author Administrator
 */
@Data
@TableName("sys_group_wallets")
public class GroupWallets {

	@Serial
	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.INPUT)
	private Long groupId;

	private Integer quotaUsed;

	private Integer quotaLimit;

	private LocalDateTime updateTime;
}
