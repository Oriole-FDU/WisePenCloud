package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ResourcePlacementGroupMountRequest {
    @NotEmpty(message = ResourceValidationMsg.RESOURCE_IDS_NOT_EMPTY)
    private List<@NotBlank(message = ResourceValidationMsg.RESOURCE_ID_NOT_BLANK) String> resourceIds;

    @NotBlank(message = ResourceValidationMsg.GROUP_ID_NOT_BLANK)
    private String groupId;

    @NotBlank(message = ResourceValidationMsg.TAG_ID_NOT_BLANK)
    private String targetTagId;
}
