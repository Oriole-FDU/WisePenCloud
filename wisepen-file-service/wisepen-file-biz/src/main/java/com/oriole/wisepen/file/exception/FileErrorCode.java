package com.oriole.wisepen.file.exception;

import com.oriole.wisepen.common.core.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件模块错误码枚举
 * 范围：2000-2999
 *
 * @author Ian.Xiong
 */
@Getter
@AllArgsConstructor
public enum FileErrorCode implements IErrorCode {

    FILE_UPLOAD_ERROR(2001, "文件上传失败"),
    FILE_CONVERT_ERROR(2002, "文件转换失败"),
    FILE_NOT_FOUND(2003, "文件不存在"),
    FILE_READ_ERROR(2004, "文件读取失败"),
    FILE_TYPE_NOT_SUPPORTED(2005, "不支持的文件类型");

    private final Integer code;
    private final String msg;
}
