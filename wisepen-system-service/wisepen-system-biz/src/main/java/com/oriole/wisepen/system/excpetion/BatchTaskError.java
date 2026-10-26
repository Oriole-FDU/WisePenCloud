package com.oriole.wisepen.system.excpetion;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.system.api.constant.SystemSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统微服务(2)批处理任务专属错误
 */
@Getter
@AllArgsConstructor
public enum BatchTaskError implements IResult {

    RESOURCE_FORK_SOURCE_NOT_FOUND(2911, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BATCH_TASK_RESOURCE_FORK, ErrorReason.NOT_FOUND), "待复制资源不存在"),
    RESOURCE_FORK_TYPE_UNSUPPORTED(2912, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BATCH_TASK_RESOURCE_FORK, ErrorReason.UNSUPPORTED), "暂不支持复制该资源类型"),
    USER_REGISTER_BATCH_SIZE_EXCEEDED(2921, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BATCH_TASK_USER_REGISTER, ErrorReason.ABOVE_UPPER_BOUND), "批量注册用户数超过上限"),
    USER_REGISTER_DEFAULT_PASSWORD_CONFIG_INVALID(2922, new ResultKey(BusinessDomain.SYSTEM, SystemSubject.BATCH_TASK_USER_REGISTER, ErrorReason.INVALID), "默认密码配置错误");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
