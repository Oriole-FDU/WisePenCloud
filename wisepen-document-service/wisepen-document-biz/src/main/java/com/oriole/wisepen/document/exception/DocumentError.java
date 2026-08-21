package com.oriole.wisepen.document.exception;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.document.api.constant.DocumentSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档微服务(6)专属业务错误
 */
@Getter
@AllArgsConstructor
public enum DocumentError implements IResult {

    // 文档相关异常
    DOCUMENT_NOT_FOUND(6111, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.NOT_FOUND),"文档不存在"),
    DOCUMENT_EDIT_SESSION_NOT_FOUND(6112, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.NOT_FOUND),"文档编辑会话不存在或已过期"),
    DOCUMENT_HAS_NO_VERSION(6112, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.STATE_INVALID),"文档尚无可用版本"),
    DOCUMENT_EDIT_SESSION_ACTIVE(6112, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.STATE_INVALID),"文档正在协同编辑，不能上传新版本"),
    DOCUMENT_PERMISSION_DENIED(6121, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.PERMISSION_DENIED),"无权访问或操作该文档"),
    DOCUMENT_EDIT_CALLBACK_INVALID(6122, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.PERMISSION_DENIED),"文档编辑回调Token校验失败"),
    CANNOT_SUPPORT_FILE_TYPE(6131, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.UNSUPPORTED),"不能处理该文件，文件类型不受支持"),
    DOCUMENT_EDIT_NOT_SUPPORTED(6132, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.UNSUPPORTED),"该文档类型暂不支持在线编辑"),
    DOCUMENT_UPLOAD_URL_APPLY_FAILED(6141, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.FAILED),"申请文档上传 URL 失败"),
    DOCUMENT_DOWNLOAD_URL_APPLY_FAILED(6142, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.FAILED),"申请文档下载 URL 失败"),
    DOCUMENT_STORAGE_STATUS_GET_FAILED(6143, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.FAILED),"获取文档存储文件状态失败"),
    DOCUMENT_REGISTER_RESOURCE_FAILED(6144, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.FAILED),"注册文档资源失败"),
    DOCUMENT_FORK_FAILED(6145, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.FAILED),"文档复制失败"),
    DOCUMENT_DOWNLOAD_FAILED(6146, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.FAILED),"文档下载失败"),
    DOCUMENT_EDIT_SAVE_FAILED(6155, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.FAILED),"文档编辑保存失败"),
    DOCUMENT_VERSION_DUPLICATED(6161, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT, ErrorReason.CONFLICT), "文档版本冲突"),

    // 文档预览相关异常
    DOCUMENT_PREVIEW_NOT_READY(6211, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT_PREVIEW, ErrorReason.STATE_INVALID),"文档尚未就绪，不能预览"),
    DOCUMENT_PREVIEW_META_NOT_FOUND(6221, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT_PREVIEW, ErrorReason.NOT_FOUND),"文档PDF META信息不存在或已损坏"),
    DOCUMENT_PREVIEW_FAILED(6231, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT_PREVIEW, ErrorReason.FAILED),"文档预览失败"),

    // 文档处理相关异常
    CANNOT_CANCEL_READY_DOCUMENT_PROCESS(6311, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT_PROCESS, ErrorReason.STATE_INVALID),"文档已就绪，不能取消处理流程"),
    CANNOT_CANCEL_DOCUMENT_PROCESS_IN_CURRENT_STATE(6312, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT_PROCESS, ErrorReason.STATE_INVALID),"文档当前状态不能取消处理流程"),
    CANNOT_RETRY_DOCUMENT_PROCESS_IN_CURRENT_STATE(6313, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT_PROCESS, ErrorReason.STATE_INVALID),"文档当前状态不能重试处理流程"),
    DOCUMENT_PROCESS_CONVERT_FAILED(6321, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT_PROCESS, ErrorReason.FAILED),"文档 PDF 转换失败"),
    DOCUMENT_PROCESS_CONTENT_READ_FAILED(6322, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT_PROCESS, ErrorReason.FAILED),"文档文本内容读取失败"),
    DOCUMENT_PROCESS_MARKDOWN_FAILED(6323, new ResultKey(BusinessDomain.DOCUMENT, DocumentSubject.DOCUMENT_PROCESS, ErrorReason.FAILED),"文档 Markdown 内容生成失败");


    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
