package com.oriole.wisepen.file.api.domain.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UploadRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Filename cannot be empty")
    private String filename;

    @NotBlank(message = "MD5 cannot be empty")
    private String md5;

    // Add other fields as needed, e.g., file type, size, etc.
}
