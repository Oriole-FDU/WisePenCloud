package com.oriole.wisepen.resource.domain.base;

import cn.hutool.core.util.IdUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSaleTierBase {
    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "售卖档位 ID")
    private String offerId = IdUtil.fastSimpleUUID();
    // 售卖详情
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "购买后可获得的权限掩码")
    private Integer grantedActionsMask; // 购买资源的用户可以获得的权限掩码

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "售卖价格")
    private Integer price; // 售卖价格

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "创建时间")
    private LocalDateTime createAt;
}
