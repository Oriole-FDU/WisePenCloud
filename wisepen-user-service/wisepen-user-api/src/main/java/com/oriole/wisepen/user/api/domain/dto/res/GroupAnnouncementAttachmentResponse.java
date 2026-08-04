package com.oriole.wisepen.user.api.domain.dto.res;

import lombok.Data;

@Data
public class GroupAnnouncementAttachmentResponse {

    private Long attachmentId;
    private String fileName;
    private Long fileSize;
    private Integer sortOrder;
}
