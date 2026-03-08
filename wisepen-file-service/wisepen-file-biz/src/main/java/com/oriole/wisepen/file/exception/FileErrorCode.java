package com.oriole.wisepen.file.exception;

import com.oriole.wisepen.common.core.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件模块错误码枚举
 * 范围：2000-2999
 *
 * @author Ian.xiong
 */
@Getter
@AllArgsConstructor
public enum FileErrorCode implements IErrorCode {

    FILE_UPLOAD_ERROR(2001, "文件上传失败"),
    FILE_CONVERT_ERROR(2002, "文件转换失败"),
    FILE_NOT_FOUND(2003, "文件不存在"),
    FILE_READ_ERROR(2004, "文件读取失败"),
    FILE_TYPE_NOT_SUPPORTED(2005, "不支持的文件类型"),
    FILE_TYPE_NOT_ALLOWED(2006, "不支持的文件类型，仅支持doc/docx/ppt/pptx/xls/xlsx/pdf"),
    FILE_MD5_MISMATCH(2007, "文件校验失败"),
    FILE_MAGIC_NUMBER_MISMATCH(2008, "文件类型与扩展名不匹配"),
    FILE_DELETE_ERROR(2009, "文件删除失败"),
    FILE_OPERATION_FORBIDDEN(2010, "当前状态不允许此操作"),
    FILE_SIZE_EXCEEDED(2011, "文件大小超过100MB限制");

    private final Integer code;
    private final String msg;
}
