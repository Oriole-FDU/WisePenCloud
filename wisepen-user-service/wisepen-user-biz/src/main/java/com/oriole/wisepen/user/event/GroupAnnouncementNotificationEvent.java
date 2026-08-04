package com.oriole.wisepen.user.event;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GroupAnnouncementNotificationEvent {

    private final Long announcementId;
    private final Long groupId;
    private final String title;
    private final String content;
    private final String bizTraceId;
    private final List<Long> receiverUserIds;
}
