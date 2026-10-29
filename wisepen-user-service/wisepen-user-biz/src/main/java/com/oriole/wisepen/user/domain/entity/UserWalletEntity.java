package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("user_wallet")
public class UserWalletEntity implements Serializable {

	@TableId(type = IdType.INPUT)
	private Long userId;

	private Integer tokenBalance;
	private Integer tokenUsed;
	private Integer coinBalance;

	private LocalDateTime updateTime;
}
