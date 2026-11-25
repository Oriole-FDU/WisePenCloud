package com.oriole.wisepen.note.exception;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.note.api.constant.NoteSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 笔记微服务(8)专属业务错误
 */
@Getter
@AllArgsConstructor
public enum NoteError implements IResult {

    // 笔记相关异常
    NOTE_NOT_FOUND(8111, new ResultKey(BusinessDomain.NOTE, NoteSubject.NOTE, ErrorReason.NOT_FOUND), "资源不存在"),
    NOTE_PERMISSION_DENIED(8121, new ResultKey(BusinessDomain.NOTE, NoteSubject.NOTE, ErrorReason.PERMISSION_DENIED), "无权访问或操作该资源"),
    NOTE_REGISTER_RESOURCE_FAILED(8131, new ResultKey(BusinessDomain.NOTE, NoteSubject.NOTE, ErrorReason.FAILED), "注册笔记资源失败"),
    NOTE_FORK_FAILED(8132, new ResultKey(BusinessDomain.NOTE, NoteSubject.NOTE, ErrorReason.FAILED), "笔记复制失败"),
    NOTE_MARKET_VERSION_CHECK_FAILED(8133, new ResultKey(BusinessDomain.NOTE, NoteSubject.NOTE, ErrorReason.FAILED), "集市笔记版本校验失败"),
    CANNOT_SUPPORT_NOTE_RESOURCE_TYPE(8141, new ResultKey(BusinessDomain.NOTE, NoteSubject.NOTE, ErrorReason.UNSUPPORTED), "不能处理该资源类型");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
