package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.oriole.wisepen.user.api.domain.base.WalletTransactionRecordBase;

import com.oriole.wisepen.user.api.enums.WalletPayerType;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("wallet_transaction_record")
public class WalletTransactionRecordEntity extends WalletTransactionRecordBase implements Serializable {
	@TableId(type = IdType.ASSIGN_ID)
	Long id;
	Long payerId; // 计费方Id
	WalletPayerType payerType; // 计费方类型
	@TableField(value = "create_time", fill = FieldFill.INSERT)
	LocalDateTime createTime; // 交易发起时间
}