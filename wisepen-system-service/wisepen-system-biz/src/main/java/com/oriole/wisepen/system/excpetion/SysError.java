package com.oriole.wisepen.system.excpetion;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.system.api.constant.SystemSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统微服务(2)专属业务错误
 */
@Getter
@AllArgsConstructor
public enum SysError implements IResult {

    MAIL_SEND_FAILED(2111, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.MAIL, ErrorReason.FAILED),"邮件发送失败"),

    FEEDBACK_NOT_FOUND(2311, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.FEEDBACK, ErrorReason.NOT_FOUND), "反馈不存在"),

    BATCH_TASK_NOT_FOUND(2211, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BATCH_TASK, ErrorReason.NOT_FOUND), "批量任务不存在"),
    BATCH_TASK_TARGET_USER_NOT_FOUND(2212, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BATCH_TASK, ErrorReason.NOT_FOUND), "批量任务目标用户不存在"),
    BATCH_TASK_TYPE_UNSUPPORTED(2221, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BATCH_TASK, ErrorReason.UNSUPPORTED), "批量任务类型不支持"),
    BATCH_TASK_STATE_INVALID(2231, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BATCH_TASK, ErrorReason.STATE_INVALID), "批量任务当前状态不允许该操作"),
    BATCH_TASK_META_INVALID(2232, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BATCH_TASK, ErrorReason.INVALID), "批量任务参数无效"),
    BATCH_TASK_ITEM_EXECUTION_FAILED(2261, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BATCH_TASK, ErrorReason.FAILED), "批量任务项执行失败"),
    BATCH_TASK_ITEM_INTERRUPTED(2262, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BATCH_TASK, ErrorReason.FAILED), "批量任务项执行中断"),

    BUSINESS_CONFIG_NOT_FOUND(2311, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BUSINESS_CONFIG, ErrorReason.NOT_FOUND), "业务配置不存在"),
    BUSINESS_CONFIG_CONTENT_INVALID(2321, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BUSINESS_CONFIG, ErrorReason.INVALID), "业务配置内容无效"),
    BUSINESS_CONFIG_REVISION_CONFLICT(2331, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BUSINESS_CONFIG, ErrorReason.CONFLICT), "业务配置已被修改"),
    BUSINESS_CONFIG_NACOS_FAILED(2361, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BUSINESS_CONFIG, ErrorReason.EXTERNAL_FAILED), "Nacos 配置操作失败");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}