package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FavoriteCollectionDeleteRequest {
    @NotBlank(message = ResourceValidationMsg.COLLECTION_ID_NOT_BLANK)
    private String collectionId;

    private Boolean keepResourcesToDefault;
}
