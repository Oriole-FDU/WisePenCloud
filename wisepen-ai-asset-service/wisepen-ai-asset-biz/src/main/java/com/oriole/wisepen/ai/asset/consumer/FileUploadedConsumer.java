package com.oriole.wisepen.ai.asset.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.ai.asset.service.IVersionService;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.file.storage.api.constant.MqTopicConstants.TOPIC_FILE_UPLOADED;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadedConsumer {

    private final IVersionService skillVersionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_FILE_UPLOADED, groupId = "wisepen-skill-upload-callback-group")
    public void onFileUploaded(String payload) throws Exception {
        // 从兼容非Java微服务的发布者订阅，使用objectMapper显式转换
        FileUploadedMessage message = objectMapper.readValue(payload, FileUploadedMessage.class);
        log.info("skill asset file upload event received. topic={} objectKey={} scene={}",
                TOPIC_FILE_UPLOADED, message.getObjectKey(), message.getScene());
        try {
            skillVersionService.handleFileUploaded(message);
            log.debug("skill asset file upload event consumed. topic={} objectKey={}",
                    TOPIC_FILE_UPLOADED, message.getObjectKey());
        } catch (Exception e) {
            log.error("skill asset file upload event consumption failed. topic={} objectKey={}",
                    TOPIC_FILE_UPLOADED, message.getObjectKey(), e);
            throw e;
        }
    }
}
