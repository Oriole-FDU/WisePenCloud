package com.oriole.wisepen.extension.fudan.exception;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.extension.fudan.constant.FudanExtensionSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FudanExtensionError implements IResult {

    UIS_TASK_NOT_FOUND(99111, new ResultKey(BusinessDomain.FUDAN_EXTENSION, FudanExtensionSubject.UIS, ErrorReason.NOT_FOUND), "任务不存在"),
    UIS_TASK_SUBMIT_FAILED(99121, new ResultKey(BusinessDomain.FUDAN_EXTENSION, FudanExtensionSubject.UIS, ErrorReason.FAILED), "UIS 认证任务提交失败"),
    UIS_REQUEST_INVALID(99131, new ResultKey(BusinessDomain.FUDAN_EXTENSION, FudanExtensionSubject.UIS, ErrorReason.INVALID), "UIS 认证请求参数无效"),

    OAUTH_REQUEST_INVALID(99211, new ResultKey(BusinessDomain.FUDAN_EXTENSION, FudanExtensionSubject.OAUTH, ErrorReason.INVALID), "OAuth2.0 请求参数无效"),
    OAUTH_TOKEN_EXCHANGE_FAILED(99221, new ResultKey(BusinessDomain.FUDAN_EXTENSION, FudanExtensionSubject.OAUTH, ErrorReason.FAILED), "OAuth2.0 令牌换取失败"),
    OAUTH_USER_INFO_FAILED(99222, new ResultKey(BusinessDomain.FUDAN_EXTENSION, FudanExtensionSubject.OAUTH, ErrorReason.FAILED), "OAuth2.0 用户信息获取失败"),
    OAUTH_USER_INFO_INCOMPLETE(99231, new ResultKey(BusinessDomain.FUDAN_EXTENSION, FudanExtensionSubject.OAUTH, ErrorReason.INVALID), "OAuth2.0 用户信息不完整");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
