package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.domain.base.TagInfoBase;
import lombok.Data;

import java.util.Map;

@Data
public class ResourceTagBindResponse {
    private String groupId;
    private String primaryTagId;
    private Boolean aiBillingApproved;
    private Map<String, TagInfoBase> tags;
}
