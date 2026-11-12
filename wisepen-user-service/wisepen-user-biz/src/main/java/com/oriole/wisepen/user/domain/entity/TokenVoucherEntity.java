package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oriole.wisepen.user.api.enums.VoucherStatus;
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
@TableName("token_voucher")
public class TokenVoucherEntity implements Serializable {

	@TableId(value="voucher_id", type = IdType.INPUT)
	private Long voucherId;

	private String code;
	private Integer amount;
	private VoucherStatus status;
	private LocalDateTime expireTime;
}
