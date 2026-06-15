package com.oriole.wisepen.ai.asset.exception;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.ai.asset.constant.AIAssetSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * skill / agent 共用的资源版本生命周期错误
 */
@Getter
@AllArgsConstructor
public enum AIResourceError implements IResult {

    AI_RESOURCE_NOT_FOUND(9011, new ResultKey(BusinessDomain.RESOURCE, AIAssetSubject.AI_RESOURCE, ErrorReason.NOT_FOUND), "资源不存在"),

    AI_RESOURCE_VERSION_NOT_FOUND(9021, new ResultKey(BusinessDomain.RESOURCE, AIAssetSubject.AI_RESOURCE_VERSION, ErrorReason.NOT_FOUND), "资源版本不存在"),
    CANNOT_OPERATE_NON_DRAFT_VERSION(9031, new ResultKey(BusinessDomain.RESOURCE, AIAssetSubject.AI_RESOURCE_VERSION, ErrorReason.STATE_INVALID), "不能操作非草稿状态的资源版本"),

    AI_RESOURCE_ASSET_NOT_READY(9041, new ResultKey(BusinessDomain.RESOURCE, AIAssetSubject.AI_RESOURCE_ASSET, ErrorReason.STATE_INVALID), "资源未就绪"),
    AI_RESOURCE_ASSET_PATH_INVALID(9051, new ResultKey(BusinessDomain.RESOURCE, AIAssetSubject.AI_RESOURCE_ASSET, ErrorReason.INVALID), "资源路径不合法"),
    AI_RESOURCE_ASSET_UPLOAD_URL_APPLY_FAILED(9061, new ResultKey(BusinessDomain.RESOURCE, AIAssetSubject.AI_RESOURCE_ASSET, ErrorReason.FAILED), "资源文件上传初始化失败");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
