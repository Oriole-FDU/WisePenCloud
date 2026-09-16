package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.user.api.enums.WalletBusinessType;
import com.oriole.wisepen.user.api.enums.WalletTransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionRecordBase {
    String traceId;
    // 操作方Id
    Long operatorId;
    // 交易量
    Integer count;
    // 交易类型
    WalletTransactionType walletTransactionType;
    // 业务类型
    WalletBusinessType walletBusinessType;
    // 元信息
    String meta;
    // Token 计费明细 JSON 快照
    String billingDetail;
}

