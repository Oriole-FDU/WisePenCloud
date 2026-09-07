package com.oriole.wisepen.media.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.media.service.IMediaService;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.file.storage.api.constant.MqTopicConstants.TOPIC_FILE_UPLOADED;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadedConsumer {

    private final IMediaService mediaService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_FILE_UPLOADED, groupId = "wisepen-media-upload-callback-group")
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_FILE_UPLOADED,
            description = "消费文件上传完成事件，更新媒体上传状态和大小并发布后续媒体处理任务。",
            payloadType = FileUploadedMessage.class,
            message = @AsyncMessage(name = "FileUploadedMessage", title = "文件上传完成事件")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-media-upload-callback-group")
    public void onFileUploaded(String payload) throws Exception {
        FileUploadedMessage message = objectMapper.readValue(payload, FileUploadedMessage.class);
        log.info("media file upload event received. topic={} objectKey={} scene={}",
                TOPIC_FILE_UPLOADED, message.getObjectKey(), message.getScene());
        try {
            mediaService.handleFileUploaded(message);
            log.debug("media file upload event consumed. topic={} objectKey={}",
                    TOPIC_FILE_UPLOADED, message.getObjectKey());
        } catch (Exception e) {
            log.error("media file upload event consumption failed. topic={} objectKey={}",
                    TOPIC_FILE_UPLOADED, message.getObjectKey(), e);
            throw e;
        }
    }
}
