package com.oriole.wisepen.document.task;

import com.oriole.wisepen.document.api.domain.base.DocumentStatus;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.config.DocumentProperties;
import com.oriole.wisepen.document.domain.entity.DocumentVersionEntity;
import com.oriole.wisepen.document.repository.DocumentVersionRepository;
import com.oriole.wisepen.document.service.IDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档服务专属垃圾回收器 (Garbage Collector)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentGcTask {

    private final DocumentVersionRepository documentVersionRepository;
    private final IDocumentService documentService;
    private final DocumentProperties documentProperties;

    @Scheduled(fixedDelayString = "${wisepen.document.stale-check.delay-ms:300000}")
    public void detectStaleUploads() {
        long start = System.currentTimeMillis();
        log.info("document gc started. task=staleUpload");
        try {
            // 查找所有正在上传的文档版本
            List<DocumentVersionEntity> uploadingDocumentVersionEntities = documentVersionRepository.findByStatus(DocumentStatusEnum.UPLOADING);
            if (uploadingDocumentVersionEntities == null || uploadingDocumentVersionEntities.isEmpty()) {
                log.info("document gc finished. task=staleUpload processed=0 timedOut=0 failed=0 costMs={}",
                        System.currentTimeMillis() - start);
                return;
            }
            log.debug("document gc candidates found. task=staleUpload pending={}", uploadingDocumentVersionEntities.size());
            LocalDateTime now = LocalDateTime.now();
            int timedOut = 0;
            for (DocumentVersionEntity entity : uploadingDocumentVersionEntities) {
                Long size = entity.getUploadMeta() != null ? entity.getUploadMeta().getSize() : null;
                long timeoutMs = calculateTimeoutMs(size);
                LocalDateTime deadline = entity.getCreateTime().plusNanos(timeoutMs * 1_000_000L);
                if (now.isAfter(deadline)) {
                    handleStaleDocument(entity);
                    timedOut++;
                }
            }
            log.info("document gc finished. task=staleUpload processed={} timedOut={} failed=0 costMs={}",
                    uploadingDocumentVersionEntities.size(), timedOut, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("document gc failed. task=staleUpload costMs={}", System.currentTimeMillis() - start, e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new IllegalStateException(e);
        }
    }

    private void handleStaleDocument(DocumentVersionEntity doc) {
        // 主动查询 Storage 并流转状态
        DocumentStatus currentStatus = documentService.refreshDocumentStatus(doc.getDocumentId());
        // 状态依然是 UPLOADING，说明 OSS 真的没有收到文件，此时标记为超时
        if (currentStatus.getStatus() == DocumentStatusEnum.UPLOADING) {
            documentService.updateStatus(doc.getDocumentId(), new DocumentStatus(DocumentStatusEnum.TRANSFER_TIMEOUT));
        }
    }

    /**
     * 根据文件大小动态计算上传超时阈值（毫秒）。
     */
    private long calculateTimeoutMs(Long size) {
        if (size == null || size <= 0) {
            return documentProperties.getUploadTimeout().getBaseTimeoutMs();
        }
        long sizeBasedMs = size * 1000L / documentProperties.getUploadTimeout().getAssumedSpeedBps();
        return Math.max(documentProperties.getUploadTimeout().getBaseTimeoutMs(),
                Math.min(documentProperties.getUploadTimeout().getMaxTimeoutMs(), sizeBasedMs));
    }
}
