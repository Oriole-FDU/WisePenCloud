package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.enums.MarketSaleStatus;
import com.oriole.wisepen.resource.enums.ResourceAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketSaleInfoResponse {
    // 预览配置
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "未购买用户可获得的权限；未配置时为空数组")
    private List<ResourceAction> reviewActions = new ArrayList<>(); // 未购买资源的用户可以获得的权限
    // 预览配置
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "可预览内容百分比")
    private int reviewContentPercentage; // 可预览内容百分比

    // 售卖信息
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "售卖档位列表")
    private List<MarketSaleTierResponse> marketSaleTiers = new ArrayList<>();

    // 售卖状态
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "售卖状态")
    private MarketSaleStatus status; // 状态

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int32", description = "指定售卖版本")
    private Integer offerVersion; // 指定售卖版本

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "审核说明")
    private String auditMessage;// 审核说明

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "审核完成时间")
    private LocalDateTime auditAt;// 审核完成时间
}
