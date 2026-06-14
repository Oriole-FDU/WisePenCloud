package com.oriole.wisepen.ai.asset.domain.dto.req;

import com.oriole.wisepen.ai.asset.constant.AIAssetValidationMsg;
import com.oriole.wisepen.ai.asset.enums.AssetResourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class AssetUploadInitRequest {
    @NotBlank(message = AIAssetValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;

    private Integer draftVersion;

    @Valid
    @NotEmpty(message = AIAssetValidationMsg.ASSET_LIST_NOT_EMPTY)
    @Builder.Default
    private List<AssetUploadRequest> assets = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetUploadRequest {
        @NotBlank(message = AIAssetValidationMsg.ASSET_NAME_NOT_BLANK)
        private String name;

        @NotBlank(message = AIAssetValidationMsg.ASSET_PATH_NOT_BLANK)
        private String path;

        @NotNull(message = AIAssetValidationMsg.ASSET_TYPE_NOT_BLANK)
        private AssetResourceType assetResourceType;

        private String md5;

        private Long expectedSize;
    }
}
