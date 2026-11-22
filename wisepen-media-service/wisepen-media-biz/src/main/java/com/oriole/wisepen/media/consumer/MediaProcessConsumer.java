package com.oriole.wisepen.media.consumer;

import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.domain.mq.MediaProcessTaskMessage;
import com.oriole.wisepen.media.exception.MediaError;
import com.oriole.wisepen.media.service.IMediaProcessService;
import com.oriole.wisepen.media.service.IMediaService;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.media.api.constant.MqTopicConstants.TOPIC_MEDIA_PROCESS;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaProcessConsumer {

    private final IMediaProcessService mediaProcessService;
    private final IMediaService mediaService;

    @KafkaListener(topics = TOPIC_MEDIA_PROCESS, groupId = "wisepen-media-process-group")
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_MEDIA_PROCESS,
            description = "消费媒体处理任务，按媒体类型完成基础产物生成和资源注册。",
            payloadType = MediaProcessTaskMessage.class,
            message = @AsyncMessage(name = "MediaProcessTaskMessage", title = "媒体处理任务")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-media-process-group")
    public void onMediaProcess(MediaProcessTaskMessage message) {
        log.info("media process event received. topic={} mediaId={}",
                TOPIC_MEDIA_PROCESS, message.getMediaId());
        try {
            mediaProcessService.processMedia(message);
            log.debug("media process event consumed. topic={} mediaId={}",
                    TOPIC_MEDIA_PROCESS, message.getMediaId());
        } catch (ServiceException e) {
            log.error("media process event consumption failed. topic={} mediaId={}",
                    TOPIC_MEDIA_PROCESS, message.getMediaId(), e);
            if (e.getErrorResult() == MediaError.MEDIA_REGISTER_RESOURCE_FAILED) {
                // finalizeToReady 已置 REGISTERING_RES_TIMEOUT，跳过
                return;
            }
            mediaService.updateStatus(message.getMediaId(), new MediaStatus(e.getMessage()));
        } catch (Exception e) {
            log.error("media process event consumption failed. topic={} mediaId={}",
                    TOPIC_MEDIA_PROCESS, message.getMediaId(), e);
            mediaService.updateStatus(message.getMediaId(), new MediaStatus(e.getMessage()));
        }
    }
}
