package com.oriole.wisepen.resource.cache;

import com.oriole.wisepen.resource.config.ResourceProperties;
import com.oriole.wisepen.resource.event.ResourceGroupDashboardMetricEvent;
import com.oriole.wisepen.user.api.constant.GroupDashboardMetricConstants;
import com.oriole.wisepen.user.api.enums.GroupDashboardSubjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisCacheManager {

    private final StringRedisTemplate stringRedisTemplate;
    private final ResourceProperties resourceProperties;

    private static final String REDIS_READ_DEDUP_PREFIX = "wisepen:resource:read:dedup:";

    /**
     * 阅读去重：在 TTL 窗口内首次访问时置位，返回 true 表示窗口内首次阅读。
     */
    public Boolean isFirstReadInWindow(String resourceId, String userId) {
        String key = REDIS_READ_DEDUP_PREFIX + resourceId + ":" + userId;
        return stringRedisTemplate.opsForValue().setIfAbsent(
                key, "1", resourceProperties.getReadCount().getDedupTtlMinutes(), TimeUnit.MINUTES
        );
    }

    @Async
    @EventListener
    public void incrementResourceGroupDashboardMetric(ResourceGroupDashboardMetricEvent event) {
        LocalDate statDate = LocalDate.now();
        String actorKey = GroupDashboardMetricConstants.actorKey(statDate, event.getGroupId(), GroupDashboardSubjectType.RESOURCE, event.getResourceId(), event.getActorUserId(), event.getMetricType());
        String actorIndexKey = GroupDashboardMetricConstants.actorIndexKey(statDate);

        stringRedisTemplate.opsForValue().increment(actorKey, event.getDelta());
        stringRedisTemplate.opsForSet().add(actorIndexKey, actorKey);

        long metricTtlDays = resourceProperties.getDashboardCache().getMetricTtlDays();
        stringRedisTemplate.expire(actorKey, metricTtlDays, TimeUnit.DAYS);
        stringRedisTemplate.expire(actorIndexKey, metricTtlDays, TimeUnit.DAYS);
    }
}
