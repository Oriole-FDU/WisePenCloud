package com.oriole.wisepen.media.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.mq.ReliablePublisher;
import com.oriole.wisepen.media.api.domain.mq.MediaJitPlaybackTaskMessage;
import com.oriole.wisepen.media.api.domain.mq.MediaProcessTaskMessage;
import com.oriole.wisepen.media.api.domain.mq.MediaReadyMessage;
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
import static com.oriole.wisepen.media.api.constant.MqTopicConstants.*;

/**
 * 媒体服务 Kafka 事件发布器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMediaEventPublisher {

    @Resource
    private ReliablePublisher reliablePublisher;

    private final ObjectMapper objectMapper;

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = TOPIC_MEDIA_PROCESS,
            description = "媒体上传完成后发布处理任务，由媒体服务异步生成预览、HLS 和资源注册。",
            payloadType = MediaProcessTaskMessage.class,
            message = @AsyncMessage(name = "MediaProcessTaskMessage", title = "媒体处理任务")
    ))
    public void publishProcessTask(MediaProcessTaskMessage message) {
        try {
            reliablePublisher.publish(TOPIC_MEDIA_PROCESS, message.getMediaId(), message, message.getMediaId());
            log.debug("media process event publish requested. topic={} mediaId={}",
                    TOPIC_MEDIA_PROCESS, message.getMediaId());
        } catch (Exception e) {
            log.error("media process event publish request failed. topic={} mediaId={}",
                    TOPIC_MEDIA_PROCESS, message.getMediaId(), e);
        }
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = TOPIC_MEDIA_JIT_PLAYBACK,
            description = "短视频播放会话创建后发布 JIT HLS 处理任务。",
            payloadType = MediaJitPlaybackTaskMessage.class,
            message = @AsyncMessage(name = "MediaJitPlaybackTaskMessage", title = "媒体 JIT 播放任务")
    ))
    public void publishJitPlaybackTask(MediaJitPlaybackTaskMessage message) {
        try {
            reliablePublisher.publish(TOPIC_MEDIA_JIT_PLAYBACK, message.getSessionId(), message, message.getSessionId());
            log.debug("media jit playback event publish requested. topic={} mediaId={}",
                    TOPIC_MEDIA_JIT_PLAYBACK, message.getMediaId());
        } catch (Exception e) {
            log.error("media jit playback event publish request failed. topic={} mediaId={}",
                    TOPIC_MEDIA_JIT_PLAYBACK, message.getMediaId(), e);
        }
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = TOPIC_MEDIA_READY,
            description = "媒体处理完成后发布就绪事件。",
            payloadType = MediaReadyMessage.class,
            message = @AsyncMessage(name = "MediaReadyMessage", title = "媒体就绪事件")
    ))
    public void publishReadyEvent(MediaReadyMessage message) {
        try {
            reliablePublisher.publish(TOPIC_MEDIA_READY, message.getResourceId(), message, message.getResourceId());
            log.debug("media ready event publish requested. topic={} resourceId={} mediaId={}",
                    TOPIC_MEDIA_READY, message.getResourceId(), message.getMediaId());
        } catch (Exception e) {
            log.error("media ready event publish request failed. topic={} resourceId={} mediaId={}",
                    TOPIC_MEDIA_READY, message.getResourceId(), message.getMediaId(), e);
        }
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = TOPIC_FILE_DELETE,
            description = "媒体资源删除或补偿时发布对象存储清理请求。",
            payloadType = String.class,
            message = @AsyncMessage(name = "MediaFileDeleteObjectKeys", title = "媒体待删除对象 Key 列表")
    ))
    public void publishFileDeleteEvent(List<String> objectKeys) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(objectKeys);
            reliablePublisher.publish(TOPIC_FILE_DELETE, null, jsonPayload, null);
            log.debug("media file delete event publish requested. topic={} count={} objectKeys={}",
                    TOPIC_FILE_DELETE, objectKeys.size(), summarizeIds(objectKeys));
        } catch (Exception e) {
            log.error("media file delete event publish request failed. topic={} count={} objectKeys={}",
                    TOPIC_FILE_DELETE, objectKeys.size(), summarizeIds(objectKeys), e);
        }
    }
}
