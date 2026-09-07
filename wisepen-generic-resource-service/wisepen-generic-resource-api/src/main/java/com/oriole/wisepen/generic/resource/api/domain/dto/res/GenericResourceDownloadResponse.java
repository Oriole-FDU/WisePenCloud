package com.oriole.wisepen.generic.resource.api.domain.dto.res;

import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Data;

/**
 * 通用资源下载响应
 */
@Data
@Builder
public class GenericResourceDownloadResponse {

    private String resourceId;
    private String resourceName;
    private ResourceType resourceType;
    private String extension;
    private Long size;
    private String downloadUrl;
}