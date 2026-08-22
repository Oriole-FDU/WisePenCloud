package com.oriole.wisepen.generic.resource.api.domain.dto.req;

import com.oriole.wisepen.generic.resource.api.constant.GenericResourceValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用资源上传初始化请求
 */
@Data
@Builder
public class GenericResourceUploadInitRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = GenericResourceValidationMsg.FILENAME_EMPTY)
    private String filename;

    /**
     * 可选扩展名。为空或无法识别时归为 UNKNOWN。
     */
    private String extension;

    @NotBlank(message = GenericResourceValidationMsg.MD5_EMPTY)
    private String md5;

    @NotNull(message = GenericResourceValidationMsg.FILE_SIZE_NULL)
    @Positive(message = GenericResourceValidationMsg.FILE_SIZE_POSITIVE)
    private Long expectedSize;

    private String mountTargetTagId;
}
