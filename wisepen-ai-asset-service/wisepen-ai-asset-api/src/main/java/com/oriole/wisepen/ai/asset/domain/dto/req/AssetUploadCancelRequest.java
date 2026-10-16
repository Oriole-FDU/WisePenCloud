package com.oriole.wisepen.ai.asset.domain.dto.req;

import com.oriole.wisepen.ai.asset.constant.AIAssetValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetUploadCancelRequest {
    @NotBlank(message = AIAssetValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;

    @NotNull(message = AIAssetValidationMsg.VERSION_NOT_BLANK)
    private Integer draftVersion;

    @NotEmpty(message = AIAssetValidationMsg.ASSET_LIST_NOT_EMPTY)
    private List<@NotBlank String> objectKeys;
}
