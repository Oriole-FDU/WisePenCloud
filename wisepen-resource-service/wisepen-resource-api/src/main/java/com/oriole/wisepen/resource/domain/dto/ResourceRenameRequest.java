package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResourceRenameRequest {
    @NotBlank(message = ResourceValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;
    @NotBlank(message = ResourceValidationMsg.RESOURCE_NEW_NAME_NOT_BLANK)
    private String newName;
}
