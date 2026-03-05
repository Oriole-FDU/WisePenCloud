package com.oriole.wisepen.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.system.api.domain.dto.SysOperLogDTO;
import com.oriole.wisepen.system.domain.entity.SysOperLog;
import com.oriole.wisepen.system.mapper.SysOperLogMapper;
import com.oriole.wisepen.system.service.SysOperLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SysOperLogServiceImpl implements SysOperLogService {

    @Autowired
    private SysOperLogMapper sysOperLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveLog(SysOperLogDTO dto) {
        SysOperLog entity = BeanUtil.copyProperties(dto, SysOperLog.class);
        int rows = sysOperLogMapper.insert(entity);
        return rows > 0;
    }
}