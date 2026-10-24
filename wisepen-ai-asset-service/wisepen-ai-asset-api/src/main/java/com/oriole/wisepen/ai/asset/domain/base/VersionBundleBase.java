package com.oriole.wisepen.ai.asset.domain.base;

import com.oriole.wisepen.ai.asset.enums.VersionStatus;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * skill / agent 版本包的公共数据字段
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class VersionBundleBase {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "版本号")
    private Integer version;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "版本状态")
    private VersionStatus status;

    @Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "版本资产列表")
    private List<AssetInfoBase> assets = new ArrayList<>();
}
