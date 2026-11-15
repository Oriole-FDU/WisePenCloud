package com.oriole.wisepen.generic.resource.exception;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.generic.resource.api.constant.GenericResourceSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用资源服务专属业务错误
 */
@Getter
@AllArgsConstructor
public enum GenericResourceError implements IResult {

    GENERIC_RESOURCE_NOT_FOUND(5451, new ResultKey(BusinessDomain.RESOURCE, GenericResourceSubject.GENERIC_RESOURCE, ErrorReason.NOT_FOUND), "通用资源不存在"),
    GENERIC_RESOURCE_UPLOAD_NOT_FOUND(5452, new ResultKey(BusinessDomain.RESOURCE, GenericResourceSubject.GENERIC_RESOURCE_UPLOAD, ErrorReason.NOT_FOUND), "通用资源上传任务不存在"),
    GENERIC_RESOURCE_PERMISSION_DENIED(5453, new ResultKey(BusinessDomain.RESOURCE, GenericResourceSubject.GENERIC_RESOURCE, ErrorReason.PERMISSION_DENIED), "无权访问或操作该通用资源"),
    CANNOT_SUPPORT_GENERIC_RESOURCE_TYPE(5454, new ResultKey(BusinessDomain.RESOURCE, GenericResourceSubject.GENERIC_RESOURCE, ErrorReason.UNSUPPORTED), "通用资源服务不能处理该文件类型"),
    GENERIC_RESOURCE_NOT_READY(5455, new ResultKey(BusinessDomain.RESOURCE, GenericResourceSubject.GENERIC_RESOURCE, ErrorReason.STATE_INVALID), "通用资源尚未就绪"),
    GENERIC_RESOURCE_UPLOAD_URL_APPLY_FAILED(5456, new ResultKey(BusinessDomain.RESOURCE, GenericResourceSubject.GENERIC_RESOURCE_UPLOAD, ErrorReason.FAILED), "申请通用资源上传 URL 失败"),
    GENERIC_RESOURCE_DOWNLOAD_URL_APPLY_FAILED(5457, new ResultKey(BusinessDomain.RESOURCE, GenericResourceSubject.GENERIC_RESOURCE, ErrorReason.FAILED), "申请通用资源下载 URL 失败"),
    GENERIC_RESOURCE_STORAGE_STATUS_GET_FAILED(5458, new ResultKey(BusinessDomain.RESOURCE, GenericResourceSubject.GENERIC_RESOURCE_UPLOAD, ErrorReason.FAILED), "获取通用资源存储状态失败"),
    GENERIC_RESOURCE_REGISTER_FAILED(5459, new ResultKey(BusinessDomain.RESOURCE, GenericResourceSubject.GENERIC_RESOURCE, ErrorReason.FAILED), "注册通用资源失败");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
