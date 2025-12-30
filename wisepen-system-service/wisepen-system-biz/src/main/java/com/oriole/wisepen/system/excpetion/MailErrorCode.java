package com.oriole.wisepen.system.excpetion;

import com.oriole.wisepen.common.core.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MailErrorCode implements IErrorCode {
    ;
    private final Integer code;
    private final String msg;
}
