package com.oriole.wisepen.resource.domain.base;

import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MarketOrderBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "交易流水号")
    private String traceId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "买家用户 ID")
    private String buyerId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "卖家用户 ID")
    private String sellerId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "购买的资源 ID")
    private String purchasedResourceId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "购买的售卖版本")
    private Integer purchasedOfferVersion;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "集市小组 ID")
    private String marketGroupId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "买家获得的权限掩码")
    private Integer buyerGrantedActionsMask;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "买家支付价格")
    private Integer buyerPaidPrice;
}
