package com.oriole.wisepen.user.api.domain.dto;

import com.oriole.wisepen.user.api.constant.UserRegexPatterns;
import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import com.oriole.wisepen.user.api.validation.ValidUsername;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

@Data
public class RegisterRequest implements Serializable {
    /** 用户名*/
    @NotBlank(message = UserValidationMsg.USERNAME_EMPTY)
<<<<<<< HEAD
<<<<<<< HEAD
    @ValidUsername
=======
    @ValidUsername(message = UserValidationMsg.CAMPUS_NO_INVALID)
>>>>>>> 2e7809a (feat(): 新增了注册、找回密码、重置密码相关功能，位于user-service的userService)
=======
    @ValidUsername
>>>>>>> a9b93c6 (fix(): 完善注册与密码找回逻辑，修复 User.status 依赖及验证问题)
    private String username;

    /** 密码*/
    @NotBlank(message = UserValidationMsg.PASSWORD_EMPTY)
    @Pattern(regexp = UserRegexPatterns.PASSWORD_PATTERN, message = UserValidationMsg.PASSWORD_INVALID)
    private String password;
}
