package com.oriole.wisepen.user.api.domain.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GroupAnnouncementPublishRequest {

    @NotNull
    private Long groupId;

    @NotBlank
    @Size(max = 5000)
    private String content;

    @Valid
    @Size(max = 10)
    private List<GroupAnnouncementAttachmentRequest> attachments;
}
