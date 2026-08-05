package com.oriole.wisepen.user.api.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GroupAnnouncementAttachmentUploadInitRequest {

    @NotNull
    private Long groupId;

    @NotBlank
    @Size(min = 32, max = 64)
    private String md5;

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9]{1,16}")
    private String extension;

    @NotNull
    @Positive
    private Long expectedSize;
}
