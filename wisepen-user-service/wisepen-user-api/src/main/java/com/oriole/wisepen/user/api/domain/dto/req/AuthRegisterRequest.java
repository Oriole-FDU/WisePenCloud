package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.constant.UserRegexPatterns;
import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import com.oriole.wisepen.user.api.validation.ValidUsername;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

@Data
public class AuthRegisterRequest implements Serializable {

    @NotBlank(message = UserValidationMsg.USERNAME_EMPTY)
    @ValidUsername
    private String username; // 用户名

    /** 密码*/
    @NotBlank(message = UserValidationMsg.PASSWORD_EMPTY)
    @Pattern(regexp = UserRegexPatterns.PASSWORD_PATTERN, message = UserValidationMsg.PASSWORD_INVALID)
    private String password; // 密码

    private String inviteCode; // 邀请码
}
