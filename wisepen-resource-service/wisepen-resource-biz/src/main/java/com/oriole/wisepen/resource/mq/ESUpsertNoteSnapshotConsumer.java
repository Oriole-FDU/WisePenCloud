package com.oriole.wisepen.resource.mq;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.ResultCode;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSnapshotResponse;
import com.oriole.wisepen.note.api.domain.mq.NoteSnapshotMessage;
import com.oriole.wisepen.note.api.feign.RemoteNoteService;
import com.oriole.wisepen.resource.domain.entity.ESIndexEntity;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.service.ISearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.oriole.wisepen.note.api.constant.MqTopicConstants.TOPIC_NOTE_SNAPSHOT;

/**
 * 笔记快照事件 → ES 正文 Upsert 消费者。
 * <p>
 * Yjs 协同服务发布的是字符串 JSON，因此沿用 {@code searchRawStringContainerFactory}；
 * 延迟 3 秒后通过 Feign 拉一遍最新版本，确保不会把过时的 plainText 落进 ES。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ESUpsertNoteSnapshotConsumer {

    private static final long DELAY_SECONDS = 3L;

    private final ObjectMapper objectMapper;
    private final ISearchSyncService searchSyncService;
    private final RemoteNoteService remoteNoteService;
    private final ScheduledExecutorService scheduler;

    @KafkaListener(topics = TOPIC_NOTE_SNAPSHOT,
            groupId = "wisepen-resource-search-note-snapshot-group",
            properties = {
                    "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
            })
    public void onNoteSnapshot(ConsumerRecord<String, String> record) {
        String payload = record.value();
        if (StrUtil.isBlank(payload)) {
            return;
        }
        NoteSnapshotMessage message;
        try {
            message = objectMapper.readValue(payload, NoteSnapshotMessage.class);
        } catch (Exception e) {
            log.error("noteSnapshot deserialize failed offset={} payload={}", record.offset(), payload, e);
            return;
        }
        if (StrUtil.isBlank(message.getResourceId())) {
            return;
        }
        String resourceId = message.getResourceId();
        log.info("noteSnapshot received resourceId={} version={} type={}",
                resourceId, message.getVersion(), message.getType());

        // 立即落库 plainText 的版本可能过时；延迟 3s 后拉最新版本，避免与协同服务的写入竞态
        String plainText = message.getPlainText();
        scheduler.schedule(() -> syncNoteContentAfterDelay(resourceId, plainText),
                DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void syncNoteContentAfterDelay(String resourceId, String fallbackPlainText) {
        try {
            R<NoteSnapshotResponse> response;
            try {
                response = remoteNoteService.getNoteLatestVersion(resourceId);
            } catch (Exception e) {
                log.warn("noteSnapshot rpc failed resourceId={}", resourceId, e);
                return;
            }
            if (response == null || response.getCode() == null
                    || response.getCode() != ResultCode.SUCCESS.getCode()
                    || response.getData() == null) {
                log.warn("noteSnapshot rpc empty resourceId={}", resourceId);
                return;
            }

            String typeExt = ResourceType.NOTE.getExtension();
            ESIndexEntity entity = ESIndexEntity.builder()
                    .id(ESIndexEntity.generateEsId(typeExt, resourceId))
                    .resourceId(resourceId)
                    .resourceType(typeExt)
                    .content(HtmlUtils.htmlEscape(StrUtil.nullToEmpty(fallbackPlainText)))
                    .build();
            searchSyncService.upsertIndexContent(entity);
            log.info("noteSnapshot indexed esId={}", entity.getId());
        } catch (Exception e) {
            log.error("noteSnapshot sync failed resourceId={}", resourceId, e);
        }
    }
}
