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
    NO_ROLE(403, "没有角色权限访问该资源");     // 对应 NotRoleException


    // 业务模块 (2000-2999)
    // ..暂无

    private final Integer code;
    private final String msg;
}