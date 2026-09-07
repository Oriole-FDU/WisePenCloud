package com.oriole.wisepen.generic.resource.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.generic.resource.service.IGenericResourceService;
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

    private final IGenericResourceService genericResourceService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_FILE_UPLOADED, groupId = "wisepen-generic-resource-upload-callback-group")
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_FILE_UPLOADED,
            description = "消费文件上传完成事件，筛选通用资源服务托管的文件并注册资源主档。",
            payloadType = FileUploadedMessage.class,
            message = @AsyncMessage(name = "FileUploadedMessage", title = "文件上传完成事件")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-generic-resource-upload-callback-group")
    public void onFileUploaded(String payload) throws Exception {
        FileUploadedMessage message;
        try {
            message = objectMapper.readValue(payload, FileUploadedMessage.class);
        } catch (Exception e) {
            log.error("generic resource file upload event parsing failed. topic={}",
                    TOPIC_FILE_UPLOADED, e);
            throw e;
        }
        log.info("generic resource file upload event received. topic={} objectKey={} scene={}",
                TOPIC_FILE_UPLOADED, message.getObjectKey(), message.getScene());
        try {
            if (message.getScene() != StorageSceneEnum.PRIVATE_GENERIC_RESOURCE || Boolean.TRUE.equals(message.getFlashUploaded())) {
                log.debug("generic resource file upload event skipped. topic={} objectKey={} scene={} flashUploaded={} reason=\"scene mismatch or flash upload\"",
                        TOPIC_FILE_UPLOADED, message.getObjectKey(), message.getScene(), message.getFlashUploaded());
                return;
            }
            genericResourceService.handleFileUploaded(message);
            log.debug("generic resource file upload event consumed. topic={} objectKey={}",
                    TOPIC_FILE_UPLOADED, message.getObjectKey());
        } catch (Exception e) {
            log.error("generic resource file upload event consumption failed. topic={} objectKey={}",
                    TOPIC_FILE_UPLOADED, message.getObjectKey(), e);
            throw e;
        }
    }
}
