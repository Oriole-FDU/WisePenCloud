package com.oriole.wisepen.file.api.domain.request;

import com.oriole.wisepen.file.api.constant.FileValidationMsg;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class FileDownloadRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @NotBlank(message = FileValidationMsg.RESOURCE_ID_EMPTY)
    private String resourceId;

    @NotBlank(message = FileValidationMsg.RESOURCE_TYPE_EMPTY)
    private String resourceType;
}
