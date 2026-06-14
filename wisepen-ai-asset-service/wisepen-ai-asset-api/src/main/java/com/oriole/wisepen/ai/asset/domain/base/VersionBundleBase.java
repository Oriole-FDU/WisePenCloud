package com.oriole.wisepen.ai.asset.domain.base;

import com.oriole.wisepen.ai.asset.enums.VersionStatus;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * skill / agent 版本包的公共数据字段
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class VersionBundleBase {

    private Integer version;

    private VersionStatus status;

    @Default
    private List<AssetInfoBase> assets = new ArrayList<>();

    // agent 运行配置；skill 不使用，恒为 null
    private AgentSpecInfoBase spec;
}
