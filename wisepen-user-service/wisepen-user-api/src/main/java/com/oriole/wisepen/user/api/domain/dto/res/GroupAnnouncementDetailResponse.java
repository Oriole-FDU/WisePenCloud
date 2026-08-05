package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class GroupAnnouncementDetailResponse {

    private Long announcementId;
    private Long groupId;
    private Long publisherId;
    private UserDisplayBase publisherInfo;
    private String content;
    private Boolean read;
    private List<GroupAnnouncementAttachmentResponse> attachments;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
