package com.oriole.wisepen.extension.fudan.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.extension.fudan.domain.dto.FudanUISTaskResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 复旦 UIS 认证任务的 Redis 快照存取组件
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisCacheManager {

    /** 用于区分复旦扩展服务任务快照的 Redis key 前缀。 */
    private static final String REDIS_FUDAN_UIS_TASK_PREFIX = "wisepen:extension:fudan:uis:task:";

    /** 任务状态的最大保留时间，超过后视为任务不存在。 */
    private static final long TASK_EXPIRE_MINUTES = 5L;

    /** Redis 字符串读写模板。 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 将任务快照序列化为 Redis JSON。 */
    private final ObjectMapper objectMapper;

    /** 将 UIS 内部任务状态写入 Redis，状态快照默认保留 5 分钟 */
    public void setUisTaskStatus(String taskId, FudanUISTaskResultDTO dto) {
        String key = REDIS_FUDAN_UIS_TASK_PREFIX + taskId;
        try {
            String jsonValue = objectMapper.writeValueAsString(dto);
            stringRedisTemplate.opsForValue().set(key, jsonValue, TASK_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("uis task snapshot serialization failed. taskId={}", taskId, e);
        }
    }

    /** 读取并反序列化 UIS 内部任务状态；缓存不存在或损坏时返回 null */
    public FudanUISTaskResultDTO getUisTaskStatus(String taskId) {
        String key = REDIS_FUDAN_UIS_TASK_PREFIX + taskId;
        String jsonValue = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(jsonValue)) return null;
        try {
            return objectMapper.readValue(jsonValue, FudanUISTaskResultDTO.class);
        } catch (JsonProcessingException e) {
            log.error("uis task snapshot deserialization failed. taskId={}", taskId, e);
            return null;
        }
    }

    /** 用户最近一次认证任务 ID 的 Redis key 前缀，供旧检查入口兼容使用 */
    private static final String REDIS_FUDAN_UIS_CURRENT_TASK_PREFIX = "wisepen:extension:fudan:uis:current-task:";

    /** 保存用户最近一次认证任务 ID，为旧检查入口提供兼容定位能力 */
    @Deprecated
    public void setCurrentTaskId(Long userId, String taskId) {
        String key = REDIS_FUDAN_UIS_CURRENT_TASK_PREFIX + userId;
        stringRedisTemplate.opsForValue().set(key, taskId, TASK_EXPIRE_MINUTES, TimeUnit.MINUTES);
    }

    /** 读取用户最近一次认证任务 ID */
    @Deprecated
    public String getCurrentTaskId(Long userId) {
        return stringRedisTemplate.opsForValue().get(REDIS_FUDAN_UIS_CURRENT_TASK_PREFIX + userId);
    }
}
