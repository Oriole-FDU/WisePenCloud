package com.oriole.wisepen.user.api.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GroupAnnouncementAttachmentRequest {

    @NotBlank
    @Size(max = 512)
    private String objectKey;

    @NotBlank
    @Size(max = 255)
    private String fileName;
}
