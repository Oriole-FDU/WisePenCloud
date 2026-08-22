package com.oriole.wisepen.generic.resource.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.mq.ReliablePublisher;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;
import static com.oriole.wisepen.file.storage.api.constant.MqTopicConstants.TOPIC_FILE_DELETE;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenericResourceEventPublisher {

    @Resource
    ReliablePublisher reliablePublisher;

    private final ObjectMapper objectMapper;

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = TOPIC_FILE_DELETE,
            description = "通用资源删除、上传补偿时发布对象存储清理请求。",
            payloadType = String.class,
            message = @AsyncMessage(name = "FileDeleteObjectKeys", title = "待删除对象 Key 列表")
    ))
    public void publishFileDeleteEvent(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        try {
            String jsonPayload = objectMapper.writeValueAsString(objectKeys);
            reliablePublisher.publish(TOPIC_FILE_DELETE, null, jsonPayload, null);
            log.debug("file delete event publish requested. topic={} count={} objectKeys={}",
                    TOPIC_FILE_DELETE, objectKeys.size(), summarizeIds(objectKeys));
        } catch (Exception e) {
            log.error("file delete event publish request failed. topic={} count={} objectKeys={}",
                    TOPIC_FILE_DELETE, objectKeys.size(), summarizeIds(objectKeys), e);
        }
    }
}
