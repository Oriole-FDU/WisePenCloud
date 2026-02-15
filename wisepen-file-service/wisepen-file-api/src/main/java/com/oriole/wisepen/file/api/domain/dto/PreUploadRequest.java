package com.oriole.wisepen.file.api.domain.dto;

import com.oriole.wisepen.file.api.constant.FileValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreUploadRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = FileValidationMsg.FILENAME_EMPTY)
    private String filename;

    @NotBlank(message = FileValidationMsg.MD5_EMPTY)
    private String md5;

    @NotNull(message = FileValidationMsg.FILESIZE_NULL)
    private Long size;
}
