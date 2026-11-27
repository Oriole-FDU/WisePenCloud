package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.enums.ResourceType;

public record CourseOutlineResourceResponse(String tagId, String resourceId, ResourceType resourceType,
                                            String resourceName, boolean read) {}
