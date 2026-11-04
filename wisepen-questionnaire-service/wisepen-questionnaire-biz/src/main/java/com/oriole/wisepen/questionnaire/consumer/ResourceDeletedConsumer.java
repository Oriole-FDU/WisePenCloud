package com.oriole.wisepen.questionnaire.consumer;

import com.oriole.wisepen.questionnaire.service.impl.QuestionnaireServiceImpl;
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

    private final QuestionnaireServiceImpl questionnaireService;

    @KafkaListener(topics = TOPIC_RESOURCE_PHYSICAL_DESTROY, groupId = "wisepen-questionnaire-physical-destroy-group")
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_RESOURCE_PHYSICAL_DESTROY,
            description = "消费资源物理删除事件，筛选问卷服务托管的资源并删除对应问卷数据。",
            payloadType = ResourceDeletedMessage.class,
            message = @AsyncMessage(name = "ResourceDeletedMessage", title = "资源物理删除事件")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-questionnaire-physical-destroy-group")
    public void onResourceDeleted(ResourceDeletedMessage message) {
        Map<ResourceType, List<String>> typedMap = message.getTypedResourceIds();
        List<String> resourceIds = new ArrayList<>();
        List<String> idsForType = typedMap.get(ResourceType.QUESTIONNAIRE);
        if (idsForType != null && !idsForType.isEmpty()) {
            resourceIds.addAll(idsForType);
        }
        log.info("questionnaire delete event received. topic={} count={} questionnaireIds={}",
                TOPIC_RESOURCE_PHYSICAL_DESTROY, resourceIds.size(), summarizeIds(resourceIds));
        if (!resourceIds.isEmpty()) {
            try {
                questionnaireService.deleteQuestionnaires(resourceIds);
                log.debug("questionnaire delete event consumed. topic={} count={} questionnaireIds={}",
                        TOPIC_RESOURCE_PHYSICAL_DESTROY, resourceIds.size(), summarizeIds(resourceIds));
            } catch (Exception e) {
                log.error("questionnaire delete event consumption failed. topic={} count={} questionnaireIds={}",
                        TOPIC_RESOURCE_PHYSICAL_DESTROY, resourceIds.size(), summarizeIds(resourceIds), e);
                throw e;
            }
        } else {
            log.info("questionnaire delete event skipped because no questionnaire-managed resources. topic={}",
                    TOPIC_RESOURCE_PHYSICAL_DESTROY);
        }
    }
}
