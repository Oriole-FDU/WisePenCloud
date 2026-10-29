package com.oriole.wisepen.media.task;

import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.domain.mq.MediaProcessTaskMessage;
import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import com.oriole.wisepen.media.config.MediaProperties;
import com.oriole.wisepen.media.domain.entity.MediaInfoEntity;
import com.oriole.wisepen.media.mq.KafkaMediaEventPublisher;
import com.oriole.wisepen.media.repository.MediaInfoRepository;
import com.oriole.wisepen.media.service.IMediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 媒体服务专属垃圾回收器 (Garbage Collector)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaGcTask {

    private final MediaInfoRepository mediaInfoRepository;
    private final IMediaService mediaService;
    private final MediaProperties mediaProperties;
    private final KafkaMediaEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${wisepen.media.stale-check.delay-ms:300000}")
    public void detectStaleUploads() {
        long start = System.currentTimeMillis();
        log.info("media gc started. task=staleUpload");
        try {
            int batchSize = Math.max(1, mediaProperties.getStaleCheck().getBatchSize());
            List<MediaInfoEntity> uploadingMediaEntities = mediaInfoRepository.findByStatus(
                    MediaStatusEnum.UPLOADING, PageRequest.of(0, batchSize));
            LocalDateTime now = LocalDateTime.now();
            int timedOut = 0;
            int failed = 0;
            for (MediaInfoEntity entity : uploadingMediaEntities) {
                try {
                    long timeoutMs = calculateTimeoutMs(entity.getSize());
                    if (entity.getCreateTime() != null
                            && now.isAfter(entity.getCreateTime().plusNanos(timeoutMs * 1_000_000L))) {
                        // 主动查询 Storage
                        MediaStatus currentStatus = mediaService.refreshMediaStatus(entity.getMediaId());
                        // 状态依然是 UPLOADING，说明 OSS 真的没有收到文件，此时标记为超时
                        if (currentStatus != null && currentStatus.getStatus() == MediaStatusEnum.UPLOADING) {
                            mediaInfoRepository.updateStatusIfCurrent(
                                    entity.getMediaId(), MediaStatusEnum.UPLOADING,
                                    new MediaStatus(MediaStatusEnum.TRANSFER_TIMEOUT));
                        }
                        timedOut++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("media gc upload candidate failed. mediaId={}", entity.getMediaId(), e);
                }
            }

            LocalDateTime processingCutoff = now.minusNanos(
                    mediaProperties.getProcessingRuntime().getProcessingTimeoutMs() * 1_000_000L);
            int requeued = requeueStale(MediaStatusEnum.PROCESSING, processingCutoff, batchSize,
                    MediaStatusEnum.UPLOADED);
            requeued += requeueStale(MediaStatusEnum.UPLOADED, processingCutoff, batchSize,
                    MediaStatusEnum.UPLOADED);
            requeued += requeueStale(MediaStatusEnum.REGISTERING_RES, processingCutoff, batchSize,
                    MediaStatusEnum.REGISTERING_RES_TIMEOUT);
            log.info("media gc finished. task=staleUpload processed={} timedOut={} requeued={} failed={} costMs={}",
                    uploadingMediaEntities.size(), timedOut, requeued, failed, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("media gc failed. task=staleUpload costMs={}", System.currentTimeMillis() - start, e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new IllegalStateException(e);
        }
    }

    private int requeueStale(MediaStatusEnum currentStatus, LocalDateTime cutoff,
                             int batchSize, MediaStatusEnum nextStatus) {
        List<MediaInfoEntity> entities = mediaInfoRepository.findStaleByStatus(
                currentStatus, cutoff, PageRequest.of(0, batchSize));
        int requeued = 0;
        for (MediaInfoEntity entity : entities) {
            try {
                if (mediaInfoRepository.updateStaleStatus(entity.getMediaId(), currentStatus, cutoff,
                        new MediaStatus(nextStatus)) > 0) {
                    eventPublisher.publishProcessTask(MediaProcessTaskMessage.builder()
                            .mediaId(entity.getMediaId())
                            .sourceObjectKey(entity.getSourceObjectKey())
                            .resourceType(entity.getResourceType())
                            .extension(entity.getSourceExtension())
                            .build());
                    requeued++;
                }
            } catch (Exception e) {
                log.warn("media gc stale processing recovery failed. mediaId={} status={}",
                        entity.getMediaId(), currentStatus, e);
            }
        }
        return requeued;
    }

    /**
     * 根据文件大小动态计算上传超时阈值（毫秒）。
     */
    private long calculateTimeoutMs(Long size) {
        if (size == null || size <= 0) {
            return mediaProperties.getUploadTimeout().getBaseTimeoutMs();
        }
        long sizeBasedMs = size * 1000L / mediaProperties.getUploadTimeout().getAssumedSpeedBps();
        return Math.max(mediaProperties.getUploadTimeout().getBaseTimeoutMs(),
                Math.min(mediaProperties.getUploadTimeout().getMaxTimeoutMs(), sizeBasedMs));
    }
}
