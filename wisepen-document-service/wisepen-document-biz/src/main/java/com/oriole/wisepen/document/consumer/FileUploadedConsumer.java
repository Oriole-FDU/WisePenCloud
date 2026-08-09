package com.oriole.wisepen.document.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.document.api.domain.base.DocumentStatus;
import com.oriole.wisepen.document.api.domain.mq.DocumentParseTaskMessage;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.domain.entity.DocumentVersionEntity;
import com.oriole.wisepen.document.mq.KafkaDocumentEventPublisher;
import com.oriole.wisepen.document.repository.DocumentVersionRepository;
import com.oriole.wisepen.document.service.IDocumentService;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;
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

    private final DocumentVersionRepository documentVersionRepository;
    private final IDocumentService documentService;
    private final KafkaDocumentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_FILE_UPLOADED, groupId = "wisepen-document-upload-callback-group")
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_FILE_UPLOADED,
            description = "消费文件上传完成事件，更新文档版本上传元数据并发布后续文档解析任务。",
            payloadType = FileUploadedMessage.class,
            message = @AsyncMessage(name = "FileUploadedMessage", title = "文件上传完成事件")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-document-upload-callback-group")
    public void onFileUploaded(String payload) throws Exception {
        // 从兼容非Java微服务的发布者订阅，使用objectMapper显式转换
        FileUploadedMessage msg = objectMapper.readValue(payload, FileUploadedMessage.class);
        log.info("document file upload event received. topic={} objectKey={} scene={}",
                TOPIC_FILE_UPLOADED, msg.getObjectKey(), msg.getScene());
        try {
            process(msg);
            log.debug("document file upload event consumed. topic={} objectKey={}",
                    TOPIC_FILE_UPLOADED, msg.getObjectKey());
        } catch (Exception e) {
            log.error("document file upload event consumption failed. topic={} objectKey={}",
                    TOPIC_FILE_UPLOADED, msg.getObjectKey(), e);
            throw e;
        }
    }

    private void process(FileUploadedMessage msg) {
        if (msg.getScene() != StorageSceneEnum.PRIVATE_DOC || Boolean.TRUE.equals(msg.getFlashUploaded())){
            log.debug("document file upload event skipped. objectKey={} scene={} flashUploaded={} reason=\"scene mismatch or flash upload\"",
                    msg.getObjectKey(), msg.getScene(), msg.getFlashUploaded());
            return; // 不处理非PRIVATE_DOC的上传通知，也不处理秒传的
        }

        DocumentVersionEntity entity = documentVersionRepository.findBySourceObjectKeyOrPreviewObjectKey(msg.getObjectKey()).orElse(null);
        if (entity == null) {
            // 用户已经取消文件处理，删除文档
            eventPublisher.publishFileDeleteEvent(List.of(msg.getObjectKey()));
            log.warn("document file upload compensated for missing document. objectKey={}",
                    msg.getObjectKey());
            return;
        }

        if (DocumentStatusEnum.UPLOADING != entity.getDocumentStatus().getStatus()) {
            log.debug("document file upload event skipped. documentId={} resourceId={} version={} objectKey={} reason=\"status mismatch\" status={}",
                    entity.getDocumentId(), entity.getResourceId(), entity.getVersion(), msg.getObjectKey(), entity.getDocumentStatus().getStatus());
            return;
        }

        // 用 OSS 回传的真实 size 覆盖
        entity.getUploadMeta().setSize(msg.getSize());
        entity = documentVersionRepository.save(entity);

        // 推进状态机
        documentService.updateStatus(entity.getDocumentId(), new DocumentStatus(DocumentStatusEnum.UPLOADED));
        eventPublisher.publishParseTask(
                DocumentParseTaskMessage.builder()
                        .documentId(entity.getDocumentId())
                        .sourceObjectKey(entity.getSourceObjectKey())
                        .fileType(entity.getUploadMeta().getFileType())
                        .build()
        );
        log.info("document file upload finished. documentId={} resourceId={} version={} objectKey={} size={}",
                entity.getDocumentId(), entity.getResourceId(), entity.getVersion(), msg.getObjectKey(), msg.getSize());
    }
}
