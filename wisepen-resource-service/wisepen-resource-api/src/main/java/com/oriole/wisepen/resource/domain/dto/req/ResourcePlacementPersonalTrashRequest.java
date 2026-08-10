package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ResourcePlacementPersonalTrashRequest {
    @NotEmpty(message = ResourceValidationMsg.RESOURCE_IDS_NOT_EMPTY)
    private List<@NotBlank(message = ResourceValidationMsg.RESOURCE_ID_NOT_BLANK) String> resourceIds;
}
