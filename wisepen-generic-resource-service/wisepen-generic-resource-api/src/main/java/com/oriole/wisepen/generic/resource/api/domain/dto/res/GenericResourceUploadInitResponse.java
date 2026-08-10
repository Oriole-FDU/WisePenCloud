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

    private String uploadId;

    /**
     * 秒传或上传补偿完成后才有资源 ID。
     */
    private String resourceId;

    private GenericResourceStatusEnum status;

    private ResourceType resourceType;

    private String extension;

    private String objectKey;

    /** OSS 预签名直传 PUT URL（flashUploaded=true 时为 null） */
    private String putUrl;

    /** 直传时需附加在 PUT 请求 Header 中的 x-oss-callback 字符串 */
    private String callbackHeader;

    private Boolean flashUploaded;
}
