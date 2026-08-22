package com.oriole.wisepen.generic.resource.api.domain.dto.res;

import com.oriole.wisepen.generic.resource.api.enums.GenericResourceStatusEnum;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用资源上传初始化响应
 */
@Data
public class GenericResourceUploadInitResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String genericResourceId;

    private String resourceId;

    private GenericResourceStatusEnum status;

    private ResourceType resourceType;

    private String extension;

    private String objectKey;

    private String putUrl;

    private String callbackHeader;

    private Boolean flashUploaded;
}
