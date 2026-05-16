package com.oriole.wisepen.resource.mq;

import cn.hutool.core.collection.CollUtil;
import com.oriole.wisepen.common.core.util.LogIdUtils;
import com.oriole.wisepen.resource.domain.entity.ESIndexEntity;
import com.oriole.wisepen.resource.domain.mq.ResourceDeletedMessage;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.service.ISearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.oriole.wisepen.resource.constant.MqTopicConstants.TOPIC_RESOURCE_PHYSICAL_DESTROY;

/**
 * 资源硬删除事件 → ES 物理删除消费者。
 * <p>
 * 与下游存储服务共享 topic，仅做 ES 侧的副本清理；topic 上 key 为 dedupKey，幂等删除安全。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ESPhysicalDestroyConsumer {

    private final ISearchSyncService searchSyncService;

    @KafkaListener(topics = TOPIC_RESOURCE_PHYSICAL_DESTROY,
            groupId = "wisepen-resource-search-physical-destroy-group-test1",
            properties = {"spring.json.value.default.type=com.oriole.wisepen.resource.domain.mq.ResourceDeletedMessage"})
    public void onResourceDestroy(ResourceDeletedMessage message) {
        if (message == null || CollUtil.isEmpty(message.getTypedResourceIds())) {
            return;
        }
        for (Map.Entry<ResourceType, List<String>> entry : message.getTypedResourceIds().entrySet()) {
            ResourceType type = entry.getKey();
            List<String> resourceIds = entry.getValue();
            if (type == null || CollUtil.isEmpty(resourceIds)) {
                continue;
            }
            for (String resourceId : resourceIds) {
                String esId = ESIndexEntity.generateEsId(type.getExtension(), resourceId);
                searchSyncService.deleteIndex(esId);
            }
            log.info("searchPhysicalDestroy deleted type={} count={} resourceIds={}",
                    type, resourceIds.size(), LogIdUtils.summarizeIds(resourceIds));
        }
    }
}
