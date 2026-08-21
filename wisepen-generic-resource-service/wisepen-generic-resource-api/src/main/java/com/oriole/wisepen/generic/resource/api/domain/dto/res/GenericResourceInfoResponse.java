package com.oriole.wisepen.generic.resource.api.domain.dto.res;

import com.oriole.wisepen.generic.resource.api.enums.GenericResourceStatusEnum;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
public class GenericResourceInfoResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String resourceId;
    private String resourceName;
    private ResourceType resourceType;
    private String extension;
    private Long size;
    private GenericResourceStatusEnum status;
}
