package com.oriole.wisepen.user.api.domain.dto.res;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
public class WalletDetailResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "已使用 Token")
    Integer tokenUsed;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "剩余 Token")
    Integer tokenBalance;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "金币余额")
    Integer coinBalance;
}
