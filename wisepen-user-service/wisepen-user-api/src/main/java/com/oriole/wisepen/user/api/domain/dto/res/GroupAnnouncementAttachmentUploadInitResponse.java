package com.oriole.wisepen.user.api.domain.dto.res;

import lombok.Data;

@Data
public class GroupAnnouncementAttachmentUploadInitResponse {

    private Boolean flashUploaded;
    private String domain;
    private String objectKey;
    private String putUrl;
    private String callbackHeader;
}
