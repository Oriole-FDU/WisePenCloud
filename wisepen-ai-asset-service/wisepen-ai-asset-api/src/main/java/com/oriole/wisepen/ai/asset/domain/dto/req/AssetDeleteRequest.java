package com.oriole.wisepen.ai.asset.domain.dto.req;

import com.oriole.wisepen.ai.asset.constant.AIAssetValidationMsg;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class AssetDeleteRequest {
    @NotBlank(message = AIAssetValidationMsg.SKILL_ID_NOT_BLANK)
    private String resourceId;

    private Integer draftVersion;

    @Valid
    @NotEmpty(message = AIAssetValidationMsg.SKILL_ASSET_LIST_NOT_EMPTY)
    private List<String> assetIds = new ArrayList<>();
}
