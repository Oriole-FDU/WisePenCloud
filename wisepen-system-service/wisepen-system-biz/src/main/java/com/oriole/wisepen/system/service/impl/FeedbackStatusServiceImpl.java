package com.oriole.wisepen.system.service.impl;

import com.oriole.wisepen.system.service.FeedbackStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 反馈状态服务实现
 * 
 * 状态流转规则：
 * - PENDING -> PROCESSING (指派/开始处理)
 * - PENDING -> IGNORED (忽略反馈)
 * - PROCESSING -> RESOLVED (标记已解决)
 * - PROCESSING -> CLOSED (关闭反馈)
 * - RESOLVED -> CLOSED (关闭反馈)
 * - IGNORED -> CLOSED (关闭反馈)
 * 
 * @author Architecture Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackStatusServiceImpl implements FeedbackStatusService {
    
    /**
     * 定义状态流转规则
     * key: 当前状态, value: 允许的后续状态列表
     */
    private static final Map<String, List<String>> STATE_TRANSITIONS = new HashMap<>();
    
    static {
        // PENDING (待处理) 可以转向 PROCESSING 或 IGNORED
        STATE_TRANSITIONS.put("PENDING", Arrays.asList("PROCESSING", "IGNORED"));
        
        // PROCESSING (处理中) 可以转向 RESOLVED 或 CLOSED
        STATE_TRANSITIONS.put("PROCESSING", Arrays.asList("RESOLVED", "CLOSED"));
        
        // RESOLVED (已解决) 可以转向 CLOSED
        STATE_TRANSITIONS.put("RESOLVED", Arrays.asList("CLOSED"));
        
        // IGNORED (忽略) 可以转向 CLOSED
        STATE_TRANSITIONS.put("IGNORED", Arrays.asList("CLOSED"));
        
        // CLOSED (已关闭) 是最终状态，不能转向其他状态
        STATE_TRANSITIONS.put("CLOSED", Arrays.asList());
    }
    
    @Override
    public boolean isValidTransition(String currentStatus, String nextStatus) {
        if (currentStatus == null || nextStatus == null) {
            return false;
        }
        
        List<String> allowedNextStatuses = STATE_TRANSITIONS.get(currentStatus);
        return allowedNextStatuses != null && allowedNextStatuses.contains(nextStatus);
    }
    
    @Override
    public List<String> getAllowedNextStatuses(String currentStatus) {
        if (currentStatus == null) {
            return Arrays.asList();
        }
        
        List<String> nextStatuses = STATE_TRANSITIONS.get(currentStatus);
        return nextStatuses != null ? nextStatuses : Arrays.asList();
    }
}
