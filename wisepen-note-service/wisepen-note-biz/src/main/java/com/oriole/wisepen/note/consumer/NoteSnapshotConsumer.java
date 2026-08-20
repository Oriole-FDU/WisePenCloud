package com.oriole.wisepen.note.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.note.api.domain.mq.NoteSnapshotMessage;
import com.oriole.wisepen.note.service.INoteVersionService;
import com.oriole.wisepen.resource.enums.ResourceType;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.oriole.wisepen.note.api.constant.MqTopicConstants.TOPIC_NOTE_SNAPSHOT;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoteSnapshotConsumer {

    private final INoteVersionService noteVersionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TOPIC_NOTE_SNAPSHOT,
            groupId = "wisepen-note-snapshot-persistence-group",
            properties = {
                    "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
            }
    )
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_NOTE_SNAPSHOT,
            description = "消费协同笔记快照事件，创建笔记版本并记录本轮编辑作者。",
            payloadType = NoteSnapshotMessage.class,
            message = @AsyncMessage(name = "NoteSnapshotMessage", title = "笔记快照事件")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-note-snapshot-persistence-group")
    public void onSnapshot(String payload) throws Exception {
        // 从非Java微服务（NodeJS）的发布者订阅，使用objectMapper显式转换
        NoteSnapshotMessage msg = objectMapper.readValue(payload, NoteSnapshotMessage.class);
        log.info("note snapshot event received. topic={} consumerGroup={} resourceId={} version={} type={}",
                TOPIC_NOTE_SNAPSHOT, "wisepen-note-snapshot-persistence-group", msg.getResourceId(), msg.getVersion(), msg.getType());
        try {
            List<Long> currentRoundAuthors = msg.getUpdatedBy() != null ? msg.getUpdatedBy().stream().map(Long::valueOf).toList() : null;
            noteVersionService.createVersion(msg, currentRoundAuthors, ResourceType.NOTE);
            log.debug("note snapshot event consumed. topic={} resourceId={} version={}",
                    TOPIC_NOTE_SNAPSHOT, msg.getResourceId(), msg.getVersion());
        } catch (Exception e) {
            log.error("note snapshot event consumption failed. topic={} resourceId={} version={}",
                    TOPIC_NOTE_SNAPSHOT, msg.getResourceId(), msg.getVersion(), e);
            throw e;
        }
    }
}
