package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.constant.UserRegexPatterns;
import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 已登录用户主动修改密码请求体
 * 
 * @author Oriole
 */
@Data
public class AuthPwdChangeRequest implements Serializable {

    @NotBlank(message = UserValidationMsg.CURRENT_PASSWORD_EMPTY)
    private String oldPassword; // 旧密码

    @NotBlank(message = UserValidationMsg.PASSWORD_EMPTY)
    @Pattern(regexp = UserRegexPatterns.PASSWORD_PATTERN, message = UserValidationMsg.PASSWORD_INVALID)
    private String newPassword; // 新密码
}
