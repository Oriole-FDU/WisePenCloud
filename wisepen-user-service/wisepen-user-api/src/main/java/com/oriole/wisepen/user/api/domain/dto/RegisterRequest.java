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
<<<<<<< HEAD
    @ValidUsername
=======
    @ValidUsername(message = UserValidationMsg.CAMPUS_NO_INVALID)
>>>>>>> 0c5e42f (feat(): 新增了注册、找回密码、重置密码相关功能，位于user-service的userService)
    private String username;

    /** 密码*/
    @NotBlank(message = UserValidationMsg.PASSWORD_EMPTY)
    @Pattern(regexp = UserRegexPatterns.PASSWORD_PATTERN, message = UserValidationMsg.PASSWORD_INVALID)
    private String password;
}
