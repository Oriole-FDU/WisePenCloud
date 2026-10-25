package com.oriole.wisepen.system.api.domain.base;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Data
public class SysOperLogBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "操作标题")
    private String title;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int32", description = "业务类型")
    private Integer businessType;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "调用方法")
    private String method;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "HTTP 请求方法")
    private String reqMethod;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "string", description = "操作用户 ID")
    private Long operUserId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "请求 URL")
    private String operUrl;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "请求 IP")
    private String operIp;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "请求参数")
    private String operParam;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "响应结果")
    private String jsonResult;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int32", description = "操作状态")
    private Integer status;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "错误信息")
    private String errorMsg;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "操作时间")
    private LocalDateTime operTime;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "string", description = "耗时")
    private Long costTime;
}
