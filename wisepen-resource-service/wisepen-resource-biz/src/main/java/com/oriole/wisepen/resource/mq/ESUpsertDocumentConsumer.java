package com.oriole.wisepen.resource.mq;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.document.api.domain.mq.DocumentReadyMessage;
import com.oriole.wisepen.resource.domain.entity.ESIndexEntity;
import com.oriole.wisepen.resource.service.ISearchSyncService;
import com.oriole.wisepen.resource.util.ESIndexBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.oriole.wisepen.document.api.constant.MqTopicConstants.TOPIC_DOCUMENT_READY;

/**
 * 文档解析完成 → ES 全量 Upsert 消费者。
 * <p>
 * 上游 Document 服务发布的是 String 类型 JSON，因此走 {@code rawStringContainerFactory} 隔离
 * 全局 JsonDeserializer。元数据 + ACL 翻译统一交给 {@link ESIndexBuilder}，
 * 本类只在其基础上把上游传来的正文塞进去做全量 Upsert。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ESUpsertDocumentConsumer {

    private final ObjectMapper objectMapper;
    private final ISearchSyncService searchSyncService;
    private final ESIndexBuilder ESIndexBuilder;

    @KafkaListener(topics = TOPIC_DOCUMENT_READY,
            groupId = "wisepen-resource-search-doc-ready-group",
            properties = {
                    "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
            })
    public void onMessage(ConsumerRecord<String, String> record) {
        String payload = record.value();
        if (StrUtil.isBlank(payload)) {
            return;
        }
        DocumentReadyMessage message;
        try {
            message = objectMapper.readValue(payload, DocumentReadyMessage.class);
        } catch (Exception e) {
            log.error("documentReady deserialize failed offset={} payload={}", record.offset(), payload, e);
            return;
        }

        String resourceId = message.getResourceId();
        if (StrUtil.isBlank(resourceId)) {
            return;
        }
        log.info("documentReady received resourceId={} contentLength={}",
                resourceId, StrUtil.length(message.getContent()));

        Optional<ESIndexEntity> entityOpt = ESIndexBuilder.build(
                resourceId, StrUtil.nullToEmpty(message.getContent()));
        if (entityOpt.isEmpty()) {
            log.warn("documentReady skipped reason=resource-not-found resourceId={}", resourceId);
            return;
        }
        ESIndexEntity entity = entityOpt.get();
        searchSyncService.upsertFullIndex(entity);
        log.info("documentReady indexed esId={} allowedUsers={} groupAcls={} tags={}",
                entity.getId(),
                entity.getAllowedUsers() == null ? 0 : entity.getAllowedUsers().size(),
                entity.getGroupAcls() == null ? 0 : entity.getGroupAcls().size(),
                entity.getTags() == null ? 0 : entity.getTags().size());
    }
}
