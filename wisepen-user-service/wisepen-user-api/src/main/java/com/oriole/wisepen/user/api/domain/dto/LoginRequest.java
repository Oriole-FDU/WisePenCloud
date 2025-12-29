package com.oriole.wisepen.user.api.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.io.Serializable;

@Data
public class LoginBody implements Serializable {
    /** 用户名或学工号 */
    @NotBlank(message = "用户名或学工号不能为空")
    private String account;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码 (预留) */
    private String code;

    /** 唯一标识 (预留，用于验证码校验) */
    private String uuid;
}