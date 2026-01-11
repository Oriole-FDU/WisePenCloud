package com.oriole.wisepen.system.api.domain.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志 DTO
 * 归属于 System 模块对外暴露的契约
 */
@Data
public class SysOperLogDTO implements Serializable {
    private String title;
    private Integer businessType;
    private String method;
    private String reqMethod;
    private Long operUserId;
    private String operUrl;
    private String operIp;
    private String operParam;
    private String jsonResult;
    private Integer status;
    private String errorMsg;
    private LocalDateTime operTime;
    private Long costTime;
}