package com.oriole.wisepen.system.service;

import com.oriole.wisepen.system.api.domain.dto.SysOperLogDTO;

public interface SysOperLogService {

    /**
     * 保存操作日志
     *
     * @param dto 日志传输对象
     * @return 是否成功
     */
    boolean saveLog(SysOperLogDTO dto);
}