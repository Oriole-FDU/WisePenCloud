package com.oriole.wisepen.ai.asset.domain.base;

import com.oriole.wisepen.ai.asset.enums.AssetUploadStatus;
import com.oriole.wisepen.ai.asset.enums.AssetResourceType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public class AssetInfoBase {
    private String id;
    private String name;
    private String path;
    private String objectKey;
    private AssetResourceType skillAssetResourceType;
    private AssetUploadStatus uploadStatus;
    private Long size;
}
