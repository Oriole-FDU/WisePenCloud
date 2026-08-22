package com.oriole.wisepen.media.exception;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.media.api.constant.MediaSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 媒体微服务专属业务错误。
 */
@Getter
@AllArgsConstructor
public enum MediaError implements IResult {

    MEDIA_NOT_FOUND(10111, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA, ErrorReason.NOT_FOUND), "媒体资源不存在"),
    MEDIA_PERMISSION_DENIED(10121, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA, ErrorReason.PERMISSION_DENIED), "无权访问或操作该媒体资源"),
    CANNOT_SUPPORT_FILE_TYPE(10131, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA, ErrorReason.UNSUPPORTED), "不能处理该文件，文件类型不受支持"),
    MEDIA_UPLOAD_URL_APPLY_FAILED(10141, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA, ErrorReason.FAILED), "申请媒体上传 URL 失败"),
    MEDIA_STORAGE_STATUS_GET_FAILED(10142, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA, ErrorReason.FAILED), "获取媒体存储文件状态失败"),
    MEDIA_REGISTER_RESOURCE_FAILED(10143, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA, ErrorReason.FAILED), "注册媒体资源失败"),

    MEDIA_PREVIEW_NOT_READY(10211, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA_PREVIEW, ErrorReason.STATE_INVALID), "媒体尚未就绪，不能预览或播放"),
    MEDIA_PLAYBACK_SESSION_NOT_FOUND(10221, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA_PREVIEW, ErrorReason.NOT_FOUND), "媒体播放会话不存在或已过期"),
    MEDIA_PLAYBACK_FAILED(10231, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA_PREVIEW, ErrorReason.FAILED), "媒体播放会话创建失败"),
    MEDIA_FORENSIC_UNAVAILABLE(10241, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA_PREVIEW, ErrorReason.UNSUPPORTED), "媒体暗水印能力不可用"),

    MEDIA_PROCESS_FAILED(10321, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA_PROCESS, ErrorReason.FAILED), "媒体处理失败"),
    CANNOT_RETRY_MEDIA_PROCESS_IN_CURRENT_STATE(10322, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA_PROCESS, ErrorReason.STATE_INVALID), "媒体当前状态不能重试处理流程"),
    CANNOT_CANCEL_READY_MEDIA_PROCESS(10323, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA_PROCESS, ErrorReason.STATE_INVALID), "媒体已就绪，不能取消处理流程"),
    CANNOT_CANCEL_MEDIA_PROCESS_IN_CURRENT_STATE(10324, new ResultKey(BusinessDomain.MEDIA, MediaSubject.MEDIA_PROCESS, ErrorReason.STATE_INVALID), "媒体当前状态不能取消处理流程");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
