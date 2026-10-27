package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.constant.UserRegexPatterns;
import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import com.oriole.wisepen.user.api.domain.base.UserInfoBase;
import com.oriole.wisepen.user.api.validation.ValidUserRegistration;
import com.oriole.wisepen.user.api.validation.ValidUsername;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@ValidUserRegistration
public class UserRegisterReqDTO extends UserInfoBase {

    @Valid
    private UserRegisterProfileDTO profile;

    @Override
    @NotBlank(message = UserValidationMsg.USERNAME_EMPTY)
    @ValidUsername
    public String getUsername() {
        return super.getUsername();
    }

    @Override
    @Pattern(regexp = UserRegexPatterns.CAMPUS_NO_PATTERN, message = UserValidationMsg.CAMPUS_NO_INVALID)
    public String getCampusNo() {
        return super.getCampusNo();
    }

    @Override
    @Pattern(regexp = UserRegexPatterns.EMAIL_PATTERN, message = UserValidationMsg.EMAIL_INVALID)
    public String getEmail() {
        return super.getEmail();
    }

    @Override
    @Pattern(regexp = UserRegexPatterns.MOBILE_PATTERN, message = UserValidationMsg.MOBILE_INVALID)
    public String getMobile() {
        return super.getMobile();
    }
}
