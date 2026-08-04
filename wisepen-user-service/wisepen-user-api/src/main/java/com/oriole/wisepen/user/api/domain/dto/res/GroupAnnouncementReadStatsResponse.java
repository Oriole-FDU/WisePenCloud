package com.oriole.wisepen.user.api.domain.dto.res;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GroupAnnouncementReadStatsResponse {

    private Long readCount;
    private Long unreadCount;
}
