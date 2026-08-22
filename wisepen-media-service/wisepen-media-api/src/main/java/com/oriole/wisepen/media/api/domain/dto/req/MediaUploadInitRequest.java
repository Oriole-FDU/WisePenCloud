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

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = MediaValidationMsg.FILENAME_EMPTY)
    private String filename;

    @NotBlank(message = MediaValidationMsg.EXTENSION_EMPTY)
    private String extension;

    @NotBlank(message = MediaValidationMsg.MD5_EMPTY)
    private String md5;

    @NotNull(message = MediaValidationMsg.FILE_SIZE_NULL)
    @Positive(message = MediaValidationMsg.FILE_SIZE_POSITIVE)
    private Long expectedSize;

    /** 可选：首次上传完成后挂载到的目标标签。 */
    private String mountTargetTagId;
}
