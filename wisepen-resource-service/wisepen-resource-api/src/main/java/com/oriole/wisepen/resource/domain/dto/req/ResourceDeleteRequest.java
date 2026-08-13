package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ResourceDeleteRequest {
    @NotEmpty(message = ResourceValidationMsg.RESOURCE_IDS_NOT_EMPTY)
    List<String> resourceIds;
}
