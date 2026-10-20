package com.oriole.wisepen.resource.domain.base;

import com.oriole.wisepen.resource.enums.MarketSaleStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Builder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSaleInfoBase {
    // 预览配置
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int32", description = "未购买用户可获得的权限掩码")
    private Integer reviewActionsMask; // 未购买资源的用户可以获得的权限掩码
    // 预览配置
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "可预览内容百分比")
    private int reviewContentPercentage; // 可预览内容百分比

    // 售卖档位信息
    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "售卖档位列表")
    private List<MarketSaleTierBase> marketSaleTiers = new ArrayList<>();

    // 售卖状态
    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "售卖状态")
    private MarketSaleStatus status = MarketSaleStatus.PENDING_REVIEW; // 状态

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int32", description = "指定售卖版本")
    private Integer offerVersion; // 指定售卖版本
}
