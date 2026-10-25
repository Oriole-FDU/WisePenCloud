package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.domain.base.UserProfileBase;
import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserProfileAdminUpdateRequest extends UserProfileBase {

    @NotNull(message = UserValidationMsg.USER_ID_EMPTY)
    private Long userId;
}
