package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.enums.ResourceAction;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Data
public class MarketSaleTierResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "售卖档位 ID")
    private String offerId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "购买后可获得的权限")
    private List<ResourceAction> grantedActions = new ArrayList<>(); // 购买资源的用户可以获得的权限掩码

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "售卖价格")
    private Integer price; // 售卖价格
}
