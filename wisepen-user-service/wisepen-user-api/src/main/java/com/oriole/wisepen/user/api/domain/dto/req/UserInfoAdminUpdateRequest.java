package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.domain.base.UserInfoBase;
import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserInfoAdminUpdateRequest extends UserInfoBase {

    @NotNull(message = UserValidationMsg.USER_ID_EMPTY)
    private Long userId;
}
