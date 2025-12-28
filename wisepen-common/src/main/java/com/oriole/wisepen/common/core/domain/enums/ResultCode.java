package com.oriole.wisepen.common.core.domain.enums;

import com.oriole.wisepen.common.core.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode implements IErrorCode {

    // 成功
    SUCCESS(200, "操作成功"),

    // 系统级
    SYSTEM_ERROR(500, "系统内部错误"),
    PARAM_ERROR(400, "参数错误"),

    // --- 认证/权限 (严格对应 Sa-Token 异常) ---
    NOT_LOGIN(401, "账号未登录"),             // 对应 NotLoginException
    NO_PERMISSION(403, "没有权限访问该资源"),   // 对应 NotPermissionException
    NO_ROLE(403, "没有角色权限访问该资源"),     // 对应 NotRoleException

    // 用户/认证模块 (1000-1999)
    USER_NOT_EXIST(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "用户名或密码错误"),
    USER_LOCKED(1003, "账号已禁用"),
    USERNAME_EXISTED(1004,"用户名已被占用"),
    USER_CAMPUS_NUM_EXISTED(1005,"该学号已注册，请通过找回密码找回"),
    EMAIL_SEND_ERROR(1006,"邮件发送失败"),
    PASSWORD_RESET_FAILED(1007,"密码重设失败"),
    //校验内容不合法(1300-1399)
    EMPTY(1300,"缺少必填内容"),
    USERNAME_INVALID(1301,"用户名必须是4-20位字母、数字或下划线"),
    PASSWORD_INVALID(1302,"密码长度必须大于8位且包含字母和数字"),
    CAMPUS_NO_INVALID(1303,"学工号格式不正确");



    // 业务模块 (2000-2999)
    // ..暂无

    private final Integer code;
    private final String msg;
}