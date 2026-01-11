package com.oriole.wisepen.common.log.service;

import com.oriole.wisepen.system.api.domain.dto.SysOperLogDTO;
import com.oriole.wisepen.system.api.feign.RemoteLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AsyncLogService {

    @Resource
    private RemoteLogService remoteLogService;

    /**
     * 异步保存日志
     * 方法调用不需要返回值，异常内部消化
     */
    @Async
    public void saveSysLog(SysOperLogDTO sysOperLog) {
        try {
            remoteLogService.saveLog(sysOperLog);
        } catch (Exception e) {
            // 日志服务挂了不能影响主业务，记录个错误日志即可
            log.error("操作日志远程落库失败: title={}, msg={}", sysOperLog.getTitle(), e.getMessage());
        }
    }
}