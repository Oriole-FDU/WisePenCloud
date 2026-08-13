package com.oriole.wisepen.common.security.exception;

import com.oriole.wisepen.common.core.constant.CommonSubject;
import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PermissionError implements IResult {
    // 2: SECURITY

    NOT_LOGIN(1211, new ResultKey(BusinessDomain.FRAMEWORK, CommonSubject.SECURITY ,ErrorReason.UNAUTHENTICATED),"未登录"),
    UNAUTHORIZED(1221, new ResultKey(BusinessDomain.FRAMEWORK, CommonSubject.SECURITY ,ErrorReason.UNAUTHORIZED) ,"当前身份角色不满足业务要求"),
    ACCOUNT_UNIDENTIFIED(1222, new ResultKey(BusinessDomain.FRAMEWORK, CommonSubject.SECURITY ,ErrorReason.UNAUTHORIZED) ,"账号未完成身份认证"),
    PERMISSION_DENIED(1231, new ResultKey(BusinessDomain.FRAMEWORK, CommonSubject.SECURITY ,ErrorReason.PERMISSION_DENIED) ,"操作权限不足");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}