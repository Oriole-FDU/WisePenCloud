package com.oriole.wisepen.common.core.domain;

import com.oriole.wisepen.common.core.domain.enums.ResultCode;
import com.oriole.wisepen.common.core.exception.IErrorCode;
import lombok.Data;
import java.io.Serializable;

/**
 * 通用响应体
 * 规范：所有返回前端的数据必须包裹在此对象中
 */
@Data
public class R<T> implements Serializable {

    private Integer code;
    private String msg;
    private T data;

    private R() {}

    private R(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // ================== 成功响应 ==================

    public static <T> R<T> ok() {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }

    // ================== 失败响应 ==================

    /**
     * 用法: R.fail(ResultCode.USER_NOT_EXIST)
     */
    public static <T> R<T> fail(IErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMsg(), null);
    }

    /**
     * 允许覆盖消息的失败响应 (用于参数校验等动态消息场景)
     * 用法: R.fail(ResultCode.PARAM_ERROR, "邮箱格式不正确")
     */
    public static <T> R<T> fail(IErrorCode errorCode, String msg) {
        return new R<>(errorCode.getCode(), msg, null);
    }

    // 兼容旧代码，但建议逐步废弃
    public static <T> R<T> fail(Integer code, String msg) {
        return new R<>(code, msg, null);
    }
}