package com.oriole.wisepen.file.api.domain.dto;

import lombok.Data;
import com.oriole.wisepen.file.api.constant.FileValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;


/**
 * @author Ian.xiong
 */
@Data
public class FileUploadRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = FileValidationMsg.FILENAME_EMPTY)
    private String filename;

    @NotBlank(message = FileValidationMsg.MD5_EMPTY)
    private String md5;

    private Long fileSize;
}
