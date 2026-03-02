package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class ResourceUpdateTagsRequest {
    @NotBlank(message = ResourceValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;
    private String groupId;
    @NotNull(message = ResourceValidationMsg.TAG_IDS_NOT_NULL)
    private List<String> tagIds;
}