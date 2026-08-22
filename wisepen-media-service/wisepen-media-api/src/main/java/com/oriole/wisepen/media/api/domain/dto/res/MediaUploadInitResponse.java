package com.oriole.wisepen.media.api.domain.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 媒体上传初始化响应。
 */
@Data
public class MediaUploadInitResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String mediaId;

    private String putUrl;

    private String callbackHeader;

    private String objectKey;

    private Boolean flashUploaded;
}
