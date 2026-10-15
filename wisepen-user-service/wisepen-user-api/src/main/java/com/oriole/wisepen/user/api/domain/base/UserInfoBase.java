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
    /** 认证大类，例如教育邮箱或校园身份认证 Provider */
    private UserVerificationMode verificationMode;

    /** 具体认证 Provider 编码，例如某校扩展服务注册的编码；非 Provider 认证方式为空 */
    private String verificationProviderCode;

    /** 用户账号状态 */
    private UserStatus userStatus;
}
