package com.oriole.wisepen.resource.exception;

import com.oriole.wisepen.common.core.exception.IErrorCode;
import lombok.AllArgsConstructor;

/**
 * 搜索域专属错误码，号段 51000-51999。
 * <p>
 * 与 {@link ResPermissionErrorCode} 同处资源服务，复用全局异常处理器 {@code GlobalExceptionHandler}
 * 的 {@link IErrorCode} 解码路径，避免每个子域自定义编码体系。
 */
@AllArgsConstructor
public enum SearchErrorCode implements IErrorCode {

    ES_CONNECTION_ERROR(51001, "Elasticsearch 集群连接异常"),
    ES_QUERY_BUILD_ERROR(51002, "搜索查询条件构建失败"),
    ES_SYNC_MESSAGE_ERROR(51003, "处理 Kafka 同步消息解析失败");

    private final Integer code;
    private final String msg;

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }
}
