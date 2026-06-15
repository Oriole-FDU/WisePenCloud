package com.oriole.wisepen.ai.asset.exception;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.ai.asset.constant.AIAssetSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 资产专属业务错误
 */
@Getter
@AllArgsConstructor
public enum AgentError implements IResult {

    // Agent相关异常
    AGENT_NOT_FOUND(9511, new ResultKey(BusinessDomain.AGENT, AIAssetSubject.AGENT, ErrorReason.NOT_FOUND), "Agent不存在"),
    AGENT_PERMISSION_DENIED(9521, new ResultKey(BusinessDomain.AGENT, AIAssetSubject.AGENT, ErrorReason.PERMISSION_DENIED), "无权访问或操作该Agent"),
    AGENT_RESOURCE_REGISTER_FAILED(9531, new ResultKey(BusinessDomain.AGENT, AIAssetSubject.AGENT, ErrorReason.FAILED), "注册Agent资源失败"),

    // Agent版本相关异常
    AGENT_PROMPT_REQUIRED(9631, new ResultKey(BusinessDomain.AGENT, AIAssetSubject.AGENT_VERSION, ErrorReason.STATE_INVALID), "Agent系统提示词不能为空");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
