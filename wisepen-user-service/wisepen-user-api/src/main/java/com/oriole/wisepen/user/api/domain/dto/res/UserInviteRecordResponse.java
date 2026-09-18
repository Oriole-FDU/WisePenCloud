package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.enums.UserInviteStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserInviteRecordResponse {

    private Long id;

    private Long inviteeUserId;

    private UserDisplayBase inviteeDisplay;

    private UserInviteStatus status;

    private LocalDateTime createTime;

    private LocalDateTime rewardTime;
}
