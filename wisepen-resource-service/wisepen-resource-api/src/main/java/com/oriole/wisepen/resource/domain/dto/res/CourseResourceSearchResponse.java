package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.enums.ResourceType;

import java.util.List;

public record CourseResourceSearchResponse(List<Hit> list, String nextCursor) {
    public record Hit(String resourceId, ResourceType resourceType, String resourceName,
                      List<String> tagIds, boolean canView, String highlightContent) {}
}
