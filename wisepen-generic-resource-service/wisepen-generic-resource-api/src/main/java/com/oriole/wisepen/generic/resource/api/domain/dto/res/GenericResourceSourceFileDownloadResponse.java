package com.oriole.wisepen.generic.resource.api.domain.dto.res;

import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用资源源文件下载响应
 */
@Data
@Builder
public class GenericResourceSourceFileDownloadResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String resourceId;
    private String resourceName;
    private ResourceType resourceType;
    private String extension;
    private Long size;
    private String downloadUrl;
}
