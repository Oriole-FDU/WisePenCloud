package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.resource.domain.base.ResourceItemInfoBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ResourceItemResponse extends ResourceItemInfoBase {
    private String resourceId;
    private List<String> currentTags;
}
