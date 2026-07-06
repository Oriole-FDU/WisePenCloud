package com.oriole.wisepen.ai.asset.domain.dto.res;

import com.oriole.wisepen.ai.asset.domain.base.SkillAssetContentInfoBase;
import com.oriole.wisepen.ai.asset.enums.VersionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillVersionBundleContentInfoResponse {
    private String resourceId;
    private Integer version;
    private VersionStatus status;

    @Builder.Default
    private List<SkillAssetContentInfoBase> assets = new ArrayList<>();
}
