package com.oriole.wisepen.user.api.domain.dto;

import com.oriole.wisepen.user.api.constant.UserRegexPatterns;
import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.io.Serializable;

/**
 * 重置密码执行请求体
 *
 * @author Oriole
 */
@Data
public class ResetExecuteRequest implements Serializable {
    /** 新密码*/
    @NotBlank(message = UserValidationMsg.PASSWORD_EMPTY)
    @Pattern(regexp = UserRegexPatterns.PASSWORD_PATTERN, message = UserValidationMsg.PASSWORD_INVALID)
    private String newPassword;
    private String token;
}