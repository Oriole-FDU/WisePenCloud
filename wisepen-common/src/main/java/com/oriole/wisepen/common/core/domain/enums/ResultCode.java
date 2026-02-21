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
    PARAM_ERROR(400, "参数错误");


    private final Integer code;
    private final String msg;
}