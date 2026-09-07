package com.oriole.wisepen.media.api.domain.dto.res;

import lombok.Data;

/**
 * 媒体上传初始化响应。
 */
@Data
public class MediaUploadInitResponse {

    private String mediaId;

    private String putUrl;

    private String callbackHeader;

    private String objectKey;

    private Boolean flashUploaded;
}