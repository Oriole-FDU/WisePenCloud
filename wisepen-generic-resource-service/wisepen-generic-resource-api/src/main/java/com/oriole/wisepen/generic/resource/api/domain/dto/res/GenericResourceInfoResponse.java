package com.oriole.wisepen.generic.resource.api.domain.dto.res;

import com.oriole.wisepen.generic.resource.api.enums.GenericResourceStatusEnum;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class GenericResourceInfoResponse {

    private String resourceId;
    private String resourceName;
    private ResourceType resourceType;
    private String extension;
    private Long size;
    private GenericResourceStatusEnum status;
}