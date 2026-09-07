package com.oriole.wisepen.generic.resource.consumer;

import com.oriole.wisepen.generic.resource.api.constant.GenericResourceConstants;
import com.oriole.wisepen.generic.resource.service.IGenericResourceService;
import com.oriole.wisepen.resource.domain.mq.ResourceDeletedMessage;
import com.oriole.wisepen.resource.enums.ResourceType;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;
import static com.oriole.wisepen.resource.constant.MqTopicConstants.TOPIC_RESOURCE_PHYSICAL_DESTROY;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceDeletedConsumer {

    private final IGenericResourceService genericResourceService;

    @KafkaListener(topics = TOPIC_RESOURCE_PHYSICAL_DESTROY, groupId = "wisepen-generic-resource-physical-destroy-group")
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_RESOURCE_PHYSICAL_DESTROY,
            description = "消费资源物理删除事件，筛选通用资源服务托管的资源并删除对应文件记录。",
            payloadType = ResourceDeletedMessage.class,
            message = @AsyncMessage(name = "ResourceDeletedMessage", title = "资源物理删除事件")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-generic-resource-physical-destroy-group")
    public void onResourceDeleted(ResourceDeletedMessage message) {
        Map<ResourceType, List<String>> typedMap = message.getTypedResourceIds();
        List<String> resourceIds = new ArrayList<>();
        if (typedMap != null) {
            for (ResourceType managedType : GenericResourceConstants.MANAGED_TYPES) {
                List<String> idsForType = typedMap.get(managedType);
                if (idsForType != null && !idsForType.isEmpty()) {
                    resourceIds.addAll(idsForType);
                }
            }
        }

        log.info("generic resource delete event received. topic={} count={} resourceIds={}",
                TOPIC_RESOURCE_PHYSICAL_DESTROY, resourceIds.size(), summarizeIds(resourceIds));
        if (!resourceIds.isEmpty()) {
            try {
                genericResourceService.deleteGenericResources(resourceIds);
                log.debug("generic resource delete event consumed. topic={} count={} resourceIds={}",
                        TOPIC_RESOURCE_PHYSICAL_DESTROY, resourceIds.size(), summarizeIds(resourceIds));
            } catch (Exception e) {
                log.error("generic resource delete event consumption failed. topic={} count={} resourceIds={}",
                        TOPIC_RESOURCE_PHYSICAL_DESTROY, resourceIds.size(), summarizeIds(resourceIds), e);
                throw e;
            }
        } else {
            log.debug("generic resource delete event skipped because no generic resources. topic={}",
                    TOPIC_RESOURCE_PHYSICAL_DESTROY);
        }
    }
}
