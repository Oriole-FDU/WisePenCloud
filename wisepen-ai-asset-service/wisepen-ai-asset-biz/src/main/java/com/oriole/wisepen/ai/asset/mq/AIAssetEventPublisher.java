package com.oriole.wisepen.ai.asset.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.mq.ReliablePublisher;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;
import static com.oriole.wisepen.file.storage.api.constant.MqTopicConstants.TOPIC_FILE_DELETE;

/**
 * 文档服务 Kafka 事件发布器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIAssetEventPublisher {

    @Resource
    ReliablePublisher reliablePublisher;
    private final ObjectMapper objectMapper;

    // 发布文件删除事件
    public void publishFileDeleteEvent(List<String> allObjectKeys) {
        try {
            // 发布至兼容非Java微服务的订阅者，统一使用 Jackson 序列化
            String jsonPayload = objectMapper.writeValueAsString(allObjectKeys);
            int count = allObjectKeys.size();
            reliablePublisher.publish(TOPIC_FILE_DELETE, null, jsonPayload, null);
            log.debug("file delete event publish requested. topic={} count={} objectKeys={}",
                    TOPIC_FILE_DELETE, count, summarizeIds(allObjectKeys));
        } catch (Exception e) {
            log.error("file delete event publish request failed. topic={} count={} objectKeys={}",
                    TOPIC_FILE_DELETE, allObjectKeys.size(), summarizeIds(allObjectKeys), e);
        }
    }
}
