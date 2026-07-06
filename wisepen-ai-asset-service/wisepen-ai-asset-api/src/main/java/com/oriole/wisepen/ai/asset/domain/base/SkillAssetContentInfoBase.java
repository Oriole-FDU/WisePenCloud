package com.oriole.wisepen.ai.asset.domain.base;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SkillAssetContentInfoBase extends AssetInfoBase {
    private String downloadUrl;
}
