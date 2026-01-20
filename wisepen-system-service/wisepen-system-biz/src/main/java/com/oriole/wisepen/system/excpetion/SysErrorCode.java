package com.oriole.wisepen.system.excpetion;

import com.oriole.wisepen.common.core.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SysErrorCode implements IErrorCode {

    MAIL_SEND_ERROR(1101, "邮件发送失败");

    private final Integer code;
    private final String msg;
}
