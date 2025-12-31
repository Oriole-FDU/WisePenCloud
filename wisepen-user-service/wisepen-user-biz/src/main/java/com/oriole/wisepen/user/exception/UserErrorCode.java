package com.oriole.wisepen.user.exception;

import com.oriole.wisepen.common.core.exception.IErrorCode;
import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户模块错误码枚举
 * 范围：1000-1999
 *
 * @author Oriole
 */
@Getter
@AllArgsConstructor
public enum UserErrorCode implements IErrorCode {

    // 用户/认证模块 (1000-1999)
    USER_NOT_EXIST(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "用户名或密码错误"),
    USER_LOCKED(1003, "账号已禁用"),
    USERNAME_EXISTED(1004, "用户名已存在"),
    EMAIL_SEND_ERROR(1005, "邮件发送失败"),
    PASSWORD_RESET_FAILED(1006, "密码重设失败");

    private final Integer code;
    private final String msg;
}