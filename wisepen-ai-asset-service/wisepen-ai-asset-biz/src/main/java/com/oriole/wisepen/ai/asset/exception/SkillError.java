package com.oriole.wisepen.ai.asset.exception;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.ai.asset.constant.AIAssetSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Skill 微服务(10)专属业务错误
 */
@Getter
@AllArgsConstructor
public enum SkillError implements IResult {

    // Skill相关异常
    SKILL_NOT_FOUND(9111, new ResultKey(BusinessDomain.SKILL, AIAssetSubject.SKILL, ErrorReason.NOT_FOUND), "Skill不存在"),
    SKILL_PERMISSION_DENIED(9121, new ResultKey(BusinessDomain.SKILL, AIAssetSubject.SKILL, ErrorReason.PERMISSION_DENIED), "无权访问或操作该Skill"),
    SKILL_RESOURCE_REGISTER_FAILED(9131, new ResultKey(BusinessDomain.SKILL, AIAssetSubject.SKILL, ErrorReason.FAILED), "注册Skill资源失败"),

    // Skill资源相关异常
    SKILL_CORE_ASSET_NOT_FOUND(9311, new ResultKey(BusinessDomain.SKILL, AIAssetSubject.SKILL_ASSET, ErrorReason.NOT_FOUND), "Skill关键资源不存在");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
