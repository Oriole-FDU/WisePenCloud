package com.oriole.wisepen.common.core.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;

/**
 * 通用响应体
 * 规范：所有返回前端的数据必须包裹在此对象中
 */
@Data
public class R<T> implements Serializable {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "业务状态码")
    private Integer code;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "业务结果标识")
    private String key;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "业务结果说明")
    private String msg;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "业务数据")
    private T data;

    private R(Integer code, String key, String msg, T data) {
        this.code = code;
        this.key = key;
        this.msg = msg;
        this.data = data;
    }

    // ================== 成功响应 ==================

    public static <T> R<T> ok() {
        return new R<>(200, "SUCCESS", "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(200, "SUCCESS", "操作成功", data);
    }

    public static <T> R<T> ok(IResult result) {
        return new R<>(200, result.getKey().toString(), result.getMsg(), null);
    }

    public static <T> R<T> ok(IResult result, T data) {
        return new R<>(200, result.getKey().toString(), result.getMsg(), data);
    }

    // ================== 失败响应 ==================

    /**
     * 用法: R.fail(ResultCode.USER_NOT_EXIST)
     */
    public static <T> R<T> fail(IResult result) {
        return new R<>(result.getCode(), result.getKey().toString(), result.getMsg(), null);
    }

    /**
     * 允许覆盖消息的失败响应
     */
    public static <T> R<T> fail(IResult result, String msg) {
        return new R<>(result.getCode(), result.getKey().toString(), msg != null ? msg : result.getMsg(), null);
    }

    /**
     * 允许覆盖消息并附加错误详情的失败响应
     */
    public static <T> R<T> fail(IResult result, String msg, T data) {
        return new R<>(result.getCode(), result.getKey().toString(), msg != null ? msg : result.getMsg(), data);
    }
}
