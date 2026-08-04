package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupAnnouncementReadMemberResponse {

    private Long userId;
    private UserDisplayBase userInfo;
    private LocalDateTime readTime;
}
