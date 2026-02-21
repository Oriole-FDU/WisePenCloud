package com.oriole.wisepen.common.security.exception;

import com.oriole.wisepen.common.core.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PermissionErrorCode implements IErrorCode {
    // --- 认证/权限 ---
    NOT_LOGIN(401, "未登录"),
    IDENTITY_UNAUTHORIZED(403, "当前身份角色不满足业务要求"),
    OPERATION_UNAUTHORIZED(403, "业务操作权限不足"),
    RESOURCE_UNAUTHORIZED(403, "资源访问权限不足");

    private final Integer code;
    private final String msg;
}