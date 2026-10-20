package com.oriole.wisepen.ai.asset.domain.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetUploadInitResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源 ID")
    private String resourceId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "草案版本号")
    private Integer version;

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资产上传票据列表")
    private List<AssetUploadTicket> assetUploadTickets = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetUploadTicket implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资产 ID")
        private String assetId;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资产目录路径")
        private String path;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资产文件名")
        private String name;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "对象存储 ObjectKey")
        private String objectKey;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "预签名直传 URL")
        private String putUrl;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "直传回调 Header")
        private String callbackHeader;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否触发秒传")
        private Boolean flashUploaded;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "对象存储域名")
        private String domain;
    }
}
