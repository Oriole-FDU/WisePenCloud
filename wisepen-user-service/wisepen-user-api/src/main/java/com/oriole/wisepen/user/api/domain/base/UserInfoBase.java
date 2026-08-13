package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class UserInfoBase extends UserDisplayBase {
    private UserVerificationMode verificationMode;
    private UserStatus userStatus;
}
