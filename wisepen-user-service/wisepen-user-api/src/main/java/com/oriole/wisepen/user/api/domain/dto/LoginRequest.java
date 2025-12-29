package com.oriole.wisepen.user.api.domain.dto;

import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.io.Serializable;

@Data
public class LoginRequest implements Serializable {
    /** 用户名或学工号 */
    @NotBlank(message = UserValidationMsg.USERNAME_EMPTY)
    private String account;

    /** 密码 */
    @NotBlank(message = UserValidationMsg.PASSWORD_EMPTY)
    private String password;

    /** 验证码 (预留) */
    private String code;

    /** 唯一标识 (预留，用于验证码校验) */
    private String uuid;
}