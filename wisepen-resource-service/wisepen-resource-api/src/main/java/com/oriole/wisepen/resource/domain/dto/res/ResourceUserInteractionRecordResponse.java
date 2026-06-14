package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.domain.base.ResourceUserInteractionRecordBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ResourceUserInteractionRecordResponse extends ResourceUserInteractionRecordBase {
    private String resourceId;
}
