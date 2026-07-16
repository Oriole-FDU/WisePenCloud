package com.oriole.wisepen.system.service;

import java.util.List;

/**
 * 反馈状态服务接口
 * 
 * @author Architecture Team
 */
public interface FeedbackStatusService {
    
    /**
     * 检查状态流转是否合法
     * 
     * @param currentStatus 当前状态
     * @param nextStatus 目标状态
     * @return 是否合法
     */
    boolean isValidTransition(String currentStatus, String nextStatus);
    
    /**
     * 获取某状态允许的后续状态列表
     * 
     * @param currentStatus 当前状态
     * @return 允许的后续状态列表
     */
    List<String> getAllowedNextStatuses(String currentStatus);
}
