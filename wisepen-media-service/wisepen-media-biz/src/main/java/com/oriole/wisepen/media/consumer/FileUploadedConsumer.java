package com.oriole.wisepen.media.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.domain.mq.MediaProcessTaskMessage;
import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import com.oriole.wisepen.media.domain.entity.MediaEntity;
import com.oriole.wisepen.media.mq.KafkaMediaEventPublisher;
import com.oriole.wisepen.media.repository.MediaEntityRepository;
import com.oriole.wisepen.media.service.IMediaService;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.oriole.wisepen.file.storage.api.constant.MqTopicConstants.TOPIC_FILE_UPLOADED;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadedConsumer {

    private final MediaEntityRepository mediaEntityRepository;
    private final IMediaService mediaService;
    private final KafkaMediaEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_FILE_UPLOADED, groupId = "wisepen-media-upload-callback-group")
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_FILE_UPLOADED,
            description = "消费文件上传完成事件，更新媒体上传状态和大小并发布后续媒体处理任务。",
            payloadType = FileUploadedMessage.class,
            message = @AsyncMessage(name = "FileUploadedMessage", title = "文件上传完成事件")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-media-upload-callback-group")
    @WithSpan("media.upload.consume")
    public void onFileUploaded(String payload) throws Exception {
        FileUploadedMessage message = objectMapper.readValue(payload, FileUploadedMessage.class);
        log.info("media file upload event received. topic={} objectKey={} scene={}",
                TOPIC_FILE_UPLOADED, message.getObjectKey(), message.getScene());
        try {
            handleFileUploaded(message);
            log.debug("media file upload event consumed. topic={} objectKey={}",
                    TOPIC_FILE_UPLOADED, message.getObjectKey());
        } catch (Exception e) {
            log.error("media file upload event consumption failed. topic={} objectKey={}",
                    TOPIC_FILE_UPLOADED, message.getObjectKey(), e);
            throw e;
        }
    }

    public void handleFileUploaded(FileUploadedMessage message) {
        if (message.getScene() != StorageSceneEnum.PRIVATE_MEDIA || Boolean.TRUE.equals(message.getFlashUploaded())) {
            log.debug("media file upload event skipped. objectKey={} scene={} flashUploaded={} reason=\"scene mismatch or flash upload\"",
                    message.getObjectKey(), message.getScene(), message.getFlashUploaded());
            return;
        }

        MediaEntity entity = mediaEntityRepository.findByStorageObjectKeyOrPreviewObjectKey(message.getObjectKey()).orElse(null);
        if (entity == null) {
            // 用户已经取消文件处理，删除媒体
            eventPublisher.publishFileDeleteEvent(List.of(message.getObjectKey()));
            log.warn("media file upload compensated for missing media. objectKey={}", message.getObjectKey());
            return;
        }

        if (entity.getMediaStatus().getStatus() != MediaStatusEnum.UPLOADING) {
            log.debug("media file upload event skipped. mediaId={} resourceId={} objectKey={} reason=\"status mismatch\" status={}",
                    entity.getMediaId(), entity.getResourceId(), message.getObjectKey(), entity.getMediaStatus().getStatus());
            return;
        }

        // 用 OSS 回传的真实 size 覆盖
        entity.getUploadMeta().setSize(message.getSize());
        entity = mediaEntityRepository.save(entity);

        mediaService.updateStatus(entity.getMediaId(), new MediaStatus(MediaStatusEnum.UPLOADED));

        eventPublisher.publishProcessTask(MediaProcessTaskMessage.builder()
                .mediaId(entity.getMediaId())
                .sourceObjectKey(message.getObjectKey())
                .resourceType(entity.getUploadMeta().getResourceType())
                .extension(entity.getUploadMeta().getSourceExtension())
                .build());
        log.info("media file upload finished. mediaId={} resourceId={} objectKey={} size={}",
                entity.getMediaId(), entity.getResourceId(), message.getObjectKey(), message.getSize());
    }
}
