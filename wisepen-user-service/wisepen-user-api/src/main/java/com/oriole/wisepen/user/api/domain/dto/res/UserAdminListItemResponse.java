package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.user.api.domain.base.UserInfoBase;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserAdminListItemResponse extends UserInfoBase {
    private Long userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
