package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.user.api.enums.WalletBusinessType;
import com.oriole.wisepen.user.api.enums.WalletTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionRecordBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "交易追踪 ID")
    String traceId;
    // 操作方Id
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "string", description = "操作方用户 ID")
    Long operatorId;
    // 交易量
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "交易量")
    Integer count;
    // 交易类型
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "交易类型")
    WalletTransactionType walletTransactionType;
    // 业务类型
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "钱包业务类型")
    WalletBusinessType walletBusinessType;
    // 元信息
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "交易元信息")
    String meta;
    // Token 计费明细 JSON 快照
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "Token 计费明细 JSON 快照")
    String billingDetail;
}

