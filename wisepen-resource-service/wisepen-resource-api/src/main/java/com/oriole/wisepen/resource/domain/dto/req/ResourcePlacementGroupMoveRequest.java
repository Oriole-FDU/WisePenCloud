package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
public class ResourcePlacementGroupMoveRequest {
    @NotBlank(message = ResourceValidationMsg.GROUP_ID_NOT_BLANK)
    private String groupId;

    @NotEmpty(message = ResourceValidationMsg.RESOURCE_SOURCE_TAG_MAP_NOT_EMPTY)
    private Map<@NotBlank(message = ResourceValidationMsg.RESOURCE_ID_NOT_BLANK) String,
            @NotBlank(message = ResourceValidationMsg.TAG_ID_NOT_BLANK) String> resourceSourceTagMap;

    @NotBlank(message = ResourceValidationMsg.TAG_ID_NOT_BLANK)
    private String targetTagId;
}
