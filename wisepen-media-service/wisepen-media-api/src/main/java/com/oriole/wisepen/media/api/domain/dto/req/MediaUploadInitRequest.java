package com.oriole.wisepen.media.api.domain.dto.req;

import com.oriole.wisepen.media.api.constant.MediaValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 媒体上传初始化请求。
 */
@Data
@Builder
public class MediaUploadInitRequest implements Serializable {

    /** 序列化版本号。 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户上传时携带的原始文件名，用于后续资源展示。 */
    @NotBlank(message = MediaValidationMsg.FILENAME_EMPTY)
    private String filename;

    /** 文件扩展名，用于判断媒体类型和处理方式。 */
    @NotBlank(message = MediaValidationMsg.EXTENSION_EMPTY)
    private String extension;

    /** 源文件 MD5，用于存储服务秒传判定。 */
    @NotBlank(message = MediaValidationMsg.MD5_EMPTY)
    private String md5;

    /** 用户端声明的文件大小，单位字节。 */
    @NotNull(message = MediaValidationMsg.FILE_SIZE_NULL)
    @Positive(message = MediaValidationMsg.FILE_SIZE_POSITIVE)
    private Long expectedSize;

    /** 可选：首次上传完成后挂载到的目标标签。 */
    private String mountTargetTagId;
}
