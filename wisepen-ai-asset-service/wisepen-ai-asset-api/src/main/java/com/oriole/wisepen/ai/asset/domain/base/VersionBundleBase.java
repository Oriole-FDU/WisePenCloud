package com.oriole.wisepen.ai.asset.domain.base;

import com.oriole.wisepen.ai.asset.enums.VersionStatus;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
public class VersionBundleBase {
    private Integer version;
    private AssetInfoBase mainSkillMD;
    private VersionStatus status;
    @Default
    private List<AssetInfoBase> skillAssets = new ArrayList<>();
}
