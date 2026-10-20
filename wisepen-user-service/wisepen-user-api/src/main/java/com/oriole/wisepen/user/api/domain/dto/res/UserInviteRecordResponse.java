package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.enums.UserInviteStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserInviteRecordResponse {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "邀请记录 ID")
    private Long id;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "被邀请用户 ID")
    private Long inviteeUserId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "被邀请用户展示信息")
    private UserDisplayBase inviteeDisplay;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "邀请状态")
    private UserInviteStatus status;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "创建时间")
    private LocalDateTime createTime;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "奖励发放时间")
    private LocalDateTime rewardTime;
}
