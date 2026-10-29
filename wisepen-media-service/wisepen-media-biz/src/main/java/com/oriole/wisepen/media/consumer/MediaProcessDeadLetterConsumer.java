package com.oriole.wisepen.media.consumer;

import com.oriole.wisepen.media.api.domain.mq.MediaProcessTaskMessage;
import com.oriole.wisepen.media.service.IMediaProcessService;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.media.api.constant.MqTopicConstants.TOPIC_MEDIA_PROCESS_DLQ;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaProcessDeadLetterConsumer {

    private final IMediaProcessService mediaProcessService;

    @KafkaListener(topics = TOPIC_MEDIA_PROCESS_DLQ, groupId = "wisepen-media-process-dlq-group")
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_MEDIA_PROCESS_DLQ,
            description = "消费媒体处理死信任务，给最终失败的媒体写入终局状态。",
            payloadType = MediaProcessTaskMessage.class,
            message = @AsyncMessage(name = "MediaProcessTaskMessage", title = "媒体处理死信任务")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-media-process-dlq-group")
    public void onMediaProcessDlq(MediaProcessTaskMessage message) {
        log.info("media process dlq event received. topic={} mediaId={}",
                TOPIC_MEDIA_PROCESS_DLQ, message.getMediaId());
        try {
            mediaProcessService.markProcessFailed(message.getMediaId(), "媒体处理任务重试耗尽");
            log.debug("media process dlq event consumed. topic={} mediaId={}",
                    TOPIC_MEDIA_PROCESS_DLQ, message.getMediaId());
        } catch (Exception e) {
            log.error("media process dlq event consumption failed. topic={} mediaId={}",
                    TOPIC_MEDIA_PROCESS_DLQ, message.getMediaId(), e);
            throw e;
        }
    }
}
