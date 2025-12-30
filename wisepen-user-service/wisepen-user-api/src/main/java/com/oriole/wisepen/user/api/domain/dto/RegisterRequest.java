package com.oriole.wisepen.user.api.domain.dto;

import com.oriole.wisepen.user.api.constant.UserRegexPatterns;
import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import com.oriole.wisepen.user.api.validation.ValidUsername;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class RegisterRequest implements Serializable {
    /** 用户名*/
    @NotBlank(message = UserValidationMsg.USERNAME_EMPTY)
    @ValidUsername(
        treatSpecialFormatAsExisting = true,
        specialFormatMessage = UserValidationMsg.USERNAME_EXISTED
    )
    private String username;

    /** 密码*/
    @NotBlank(message = UserValidationMsg.PASSWORD_EMPTY)
    @Pattern(regexp = UserRegexPatterns.PASSWORD_PATTERN, message = UserValidationMsg.PASSWORD_INVALID)
    private String password;

    /** 真名*/
    @NotBlank(message = UserValidationMsg.REAL_NAME_EMPTY)
    @Size(max = 64, message = UserValidationMsg.REAL_NAME_TOO_LONG)
    private String realName;

    /** 学工号*/
    @NotBlank(message = UserValidationMsg.CAMPUS_NO_EMPTY)
    @Pattern(regexp = UserRegexPatterns.CAMPUS_NO_PATTERN, message = UserValidationMsg.CAMPUS_NO_INVALID)
    private String campusNo;
}
