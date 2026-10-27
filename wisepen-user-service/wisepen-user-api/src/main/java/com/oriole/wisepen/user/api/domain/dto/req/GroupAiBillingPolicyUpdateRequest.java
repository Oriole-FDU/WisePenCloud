package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.constant.GroupValidationMsg;
import com.oriole.wisepen.user.api.enums.AiBillingPolicy;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupAiBillingPolicyUpdateRequest {

    @NotNull(message = GroupValidationMsg.GROUP_ID_NOT_NULL)
    private Long groupId;

    @NotNull
    private AiBillingPolicy aiBillingPolicy;
}
