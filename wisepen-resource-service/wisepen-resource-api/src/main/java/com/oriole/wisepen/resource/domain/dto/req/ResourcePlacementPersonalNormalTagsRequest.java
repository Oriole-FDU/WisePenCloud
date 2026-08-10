package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ResourcePlacementPersonalNormalTagsRequest {
    @NotEmpty(message = ResourceValidationMsg.RESOURCE_IDS_NOT_EMPTY)
    private List<@NotBlank(message = ResourceValidationMsg.RESOURCE_ID_NOT_BLANK) String> resourceIds;

    @NotNull(message = ResourceValidationMsg.TAG_IDS_NOT_NULL)
    private List<@NotBlank(message = ResourceValidationMsg.TAG_ID_NOT_BLANK) String> normalTagIds;
}
