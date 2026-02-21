package com.oriole.wisepen.common.security.exception;

import lombok.Getter;

@Getter
public class PermissionException extends RuntimeException {

    private final Integer code;
    private final String type;

    public PermissionException(PermissionErrorCode errorCode) {
        super(errorCode.getMsg());
        this.type = errorCode.name();
        this.code = errorCode.getCode();
    }
}