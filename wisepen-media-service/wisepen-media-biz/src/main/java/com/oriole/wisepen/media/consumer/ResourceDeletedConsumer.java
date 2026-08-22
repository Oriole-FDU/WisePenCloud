package com.oriole.wisepen.media.consumer;

import com.oriole.wisepen.media.api.constant.MediaConstants;
import com.oriole.wisepen.media.service.IMediaService;
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

    private final IMediaService mediaService;

    @KafkaListener(topics = TOPIC_RESOURCE_PHYSICAL_DESTROY, groupId = "wisepen-media-physical-destroy-group")
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_RESOURCE_PHYSICAL_DESTROY,
            description = "消费资源物理删除事件，筛选媒体服务托管的图片、视频和音频资源并删除对应媒体数据。",
            payloadType = ResourceDeletedMessage.class,
            message = @AsyncMessage(name = "ResourceDeletedMessage", title = "资源物理删除事件")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-media-physical-destroy-group")
    public void onResourceDeleted(ResourceDeletedMessage message) {
        Map<ResourceType, List<String>> typedMap = message.getTypedResourceIds();
        List<String> resourceIds = new ArrayList<>();
        for (ResourceType allowedType : MediaConstants.ALLOWED_TYPES) {
            List<String> idsForType = typedMap.get(allowedType);
            if (idsForType != null && !idsForType.isEmpty()) {
                resourceIds.addAll(idsForType);
            }
        }

        log.info("media resource delete event received. topic={} count={} resourceIds={}",
                TOPIC_RESOURCE_PHYSICAL_DESTROY, resourceIds.size(), summarizeIds(resourceIds));
        if (resourceIds.isEmpty()) {
            log.debug("media resource delete event skipped because no media resources. topic={}",
                    TOPIC_RESOURCE_PHYSICAL_DESTROY);
            return;
        }

        try {
            mediaService.deleteMediaByResourceIds(resourceIds);
            log.debug("media resource delete event consumed. topic={} count={} resourceIds={}",
                    TOPIC_RESOURCE_PHYSICAL_DESTROY, resourceIds.size(), summarizeIds(resourceIds));
        } catch (Exception e) {
            log.error("media resource delete event consumption failed. topic={} count={} resourceIds={}",
                    TOPIC_RESOURCE_PHYSICAL_DESTROY, resourceIds.size(), summarizeIds(resourceIds), e);
            throw e;
        }
    }

}
