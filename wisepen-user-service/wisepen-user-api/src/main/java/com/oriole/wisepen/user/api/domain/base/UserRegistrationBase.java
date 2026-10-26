package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.user.api.constant.UserRegexPatterns;
import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import com.oriole.wisepen.user.api.validation.ValidUserRegistration;
import com.oriole.wisepen.user.api.validation.ValidUsername;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 注册入参的公共载荷基类。
 * <p>
 * 既作为 {@code UserDefaultPasswordRegisterRequest} 的父类，也被批量注册任务 meta 复用，
 * 因此属于跨端点的共享入参结构，不命名为 {@code *Request}。
 */
@ValidUserRegistration
@Data
public class UserRegistrationBase implements Serializable {

    @NotBlank(message = UserValidationMsg.USERNAME_EMPTY)
    @ValidUsername
    private String username;

    @Positive(message = UserValidationMsg.USER_ID_INVALID)
    private Long userId;

    private String nickname;
    private String avatar;
    private IdentityType identityType;
    private String realName;

    @Pattern(regexp = UserRegexPatterns.CAMPUS_NO_PATTERN, message = UserValidationMsg.CAMPUS_NO_INVALID)
    private String campusNo;

    @Pattern(regexp = UserRegexPatterns.EMAIL_PATTERN, message = UserValidationMsg.EMAIL_INVALID)
    private String email;

    @Pattern(regexp = UserRegexPatterns.MOBILE_PATTERN, message = UserValidationMsg.MOBILE_INVALID)
    private String mobile;

    private UserVerificationMode verificationMode;
    private String verificationProviderCode;
    private UserStatus userStatus;
    private UserRegistrationProfileBase profile;
}
