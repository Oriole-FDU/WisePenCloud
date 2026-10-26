package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.domain.base.UserRegistrationBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserDefaultPasswordRegisterRequest extends UserRegistrationBase {
    private String inviteCode;
}
