package com.oriole.wisepen.common.core.exception;

import lombok.Getter;

/**
 * 业务异常：用于在 Service 层中断逻辑并抛出错误码
 */
@Getter
public class ServiceException extends RuntimeException {

    private final Integer code;

    public ServiceException(IErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
    }

    public ServiceException(String msg) {
        super(msg);
        this.code = 500;
    }
}