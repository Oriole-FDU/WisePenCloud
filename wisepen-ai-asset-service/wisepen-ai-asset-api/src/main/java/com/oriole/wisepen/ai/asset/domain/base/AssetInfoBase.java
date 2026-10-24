package com.oriole.wisepen.ai.asset.domain.base;

import com.oriole.wisepen.ai.asset.enums.AssetUploadStatus;
import com.oriole.wisepen.ai.asset.enums.AssetResourceType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@SuperBuilder
@NoArgsConstructor
public class AssetInfoBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资产 ID")
    private String id;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资产文件名")
    private String name;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资产所在目录路径")
    private String path;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "对象存储 ObjectKey；草稿资产在首次申请上传票据前为空")
    private String objectKey;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资产资源类型")
    private AssetResourceType assetResourceType;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资产上传状态")
    private AssetUploadStatus uploadStatus;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "string", description = "资产文件大小")
    private Long size;
    // 替换上传成功前，objectKey、assetResourceType、size 始终表示当前正式文件
    private AssetPendingUploadBase pendingUpload;
}
