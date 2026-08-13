package com.oriole.wisepen.resource.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.document.api.domain.mq.DocumentReadyMessage;
import com.oriole.wisepen.note.api.domain.enums.VersionType;
import com.oriole.wisepen.note.api.domain.mq.NoteSnapshotMessage;
import com.oriole.wisepen.resource.service.ISearchSyncService;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.document.api.constant.MqTopicConstants.TOPIC_DOCUMENT_READY;
import static com.oriole.wisepen.note.api.constant.MqTopicConstants.TOPIC_NOTE_SNAPSHOT;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceContentConsumer {

    private final ISearchSyncService searchSyncService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_DOCUMENT_READY, groupId = "wisepen-document-ready-group")
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_DOCUMENT_READY,
            description = "消费文档就绪事件，将文档解析后的文本内容同步到资源搜索索引。",
            payloadType = DocumentReadyMessage.class,
            message = @AsyncMessage(name = "DocumentReadyMessage", title = "文档就绪事件")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-document-ready-group")
    public void onDocumentReady(DocumentReadyMessage message) throws Exception {
        log.info("document ready event received. topic={} resourceId={} version={} contentLength={}",
                TOPIC_DOCUMENT_READY, message.getResourceId(), message.getVersion(), message.getContent()!=null ? message.getContent().length() : 0);
        try {
            searchSyncService.syncResourceContent(message.getResourceId(), message.getContent());
            log.debug("document ready event consumed. topic={} resourceId={} version={}",
                    TOPIC_DOCUMENT_READY, message.getResourceId(), message.getVersion());
        } catch (Exception e) {
            log.error("document ready event consumption failed. topic={} resourceId={} version={}",
                    TOPIC_DOCUMENT_READY, message.getResourceId(), message.getVersion(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = TOPIC_NOTE_SNAPSHOT,
            groupId = "wisepen-note-snapshot-search-sync-group",
            properties = {
                    "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
            }
    )
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_NOTE_SNAPSHOT,
            description = "消费协同笔记快照事件，将完整快照的纯文本内容同步到资源搜索索引。",
            payloadType = NoteSnapshotMessage.class,
            message = @AsyncMessage(name = "NoteSnapshotMessage", title = "笔记快照事件")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-note-snapshot-search-sync-group")
    public void onSnapshot(String payload) throws Exception {
        // 从非Java微服务（NodeJS）的发布者订阅，使用objectMapper显式转换
        NoteSnapshotMessage msg = objectMapper.readValue(payload, NoteSnapshotMessage.class);
        log.info("note snapshot event received. topic={} consumerGroup={} resourceId={} contentLength={}",
                TOPIC_NOTE_SNAPSHOT, "wisepen-note-snapshot-search-sync-group",
                msg.getResourceId(), msg.getPlainText()!=null ? msg.getPlainText().length() : 0);
        try {
            if (VersionType.FULL == msg.getType()) {
                searchSyncService.syncResourceContent(msg.getResourceId(), msg.getPlainText());
                log.debug("note snapshot event consumed. topic={} resourceId={}",
                        TOPIC_NOTE_SNAPSHOT, msg.getResourceId());
            } else {
                log.info("note snapshot event skipped because type is not FULL. topic={} resourceId={} type={}",
                        TOPIC_NOTE_SNAPSHOT, msg.getResourceId(), msg.getType());
            }

        } catch (Exception e) {
            log.error("note snapshot event consumption failed. topic={} resourceId={}", TOPIC_NOTE_SNAPSHOT, msg.getResourceId(), e);
            throw e;
        }
    }
}
