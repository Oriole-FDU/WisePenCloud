package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import com.oriole.wisepen.resource.enums.ResourceTagUpdateMode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchResourceUpdateTagsRequest {
    @NotEmpty(message = ResourceValidationMsg.RESOURCE_IDS_NOT_EMPTY)
    private List<String> resourceIds;
    private String groupId;
    @NotNull(message = ResourceValidationMsg.TAG_IDS_NOT_NULL)
    private List<String> tagIds;
    private ResourceTagUpdateMode mode = ResourceTagUpdateMode.REPLACE;
}
