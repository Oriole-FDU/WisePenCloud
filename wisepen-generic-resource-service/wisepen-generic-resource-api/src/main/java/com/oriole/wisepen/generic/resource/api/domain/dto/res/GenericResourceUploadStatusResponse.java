package com.oriole.wisepen.generic.resource.api.domain.dto.res;

import com.oriole.wisepen.generic.resource.api.enums.GenericResourceStatusEnum;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用资源上传状态响应
 */
@Data
public class GenericResourceUploadStatusResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String uploadId;
    private String resourceId;
    private String resourceName;
    private ResourceType resourceType;
    private String extension;
    private String objectKey;
    private Long size;
    private GenericResourceStatusEnum status;
}
