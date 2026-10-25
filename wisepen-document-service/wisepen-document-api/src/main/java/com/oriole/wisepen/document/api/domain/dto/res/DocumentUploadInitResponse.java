package com.oriole.wisepen.document.api.domain.dto.res;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文档上传初始化响应
 */
@Data
public class DocumentUploadInitResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文档版本 ID，用于后续刷新、重试或取消本次处理任务 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "文档版本 ID")
    private String documentId;

    /** OSS 预签名直传 PUT URL（flashUploaded=true 时为 null） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "预签名直传 URL")
    private String putUrl;

    /** 直传时需附加在 PUT 请求 Header 中的 x-oss-callback 字符串 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "直传回调 Header")
    private String callbackHeader;

    /** 文件在 OSS 中的 ObjectKey */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "对象存储 ObjectKey")
    private String objectKey;

    /** 是否触发秒传（true 表示文件已存在，无需上传） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否触发秒传")
    private Boolean flashUploaded;
}
