package com.oriole.wisepen.media.task;

import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import com.oriole.wisepen.media.config.MediaProperties;
import com.oriole.wisepen.media.domain.entity.MediaInfoEntity;
import com.oriole.wisepen.media.repository.MediaInfoRepository;
import com.oriole.wisepen.media.service.IMediaProcessService;
import com.oriole.wisepen.media.service.IMediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final IMediaProcessService mediaProcessService;
    private final MediaProperties mediaProperties;

    @Scheduled(fixedDelayString = "${wisepen.media.stale-check-delay-ms:300000}")
    public void detectStaleUploads() {
        long start = System.currentTimeMillis();
        log.info("media gc started. task=staleUpload");
        try {
            // 查找所有正在上传的媒体
            List<MediaInfoEntity> uploadingMediaEntities = mediaInfoRepository.findByStatus(MediaStatusEnum.UPLOADING);
            if (uploadingMediaEntities == null || uploadingMediaEntities.isEmpty()) {
                log.info("media gc finished. task=staleUpload processed=0 timedOut=0 failed=0 costMs={}",
                        System.currentTimeMillis() - start);
                return;
            }
            log.debug("media gc candidates found. task=staleUpload pending={}", uploadingMediaEntities.size());
            LocalDateTime now = LocalDateTime.now();
            int timedOut = 0;
            for (MediaInfoEntity entity : uploadingMediaEntities) {
                Long size = entity.getSize();
                long timeoutMs = calculateTimeoutMs(size);
                LocalDateTime deadline = entity.getCreateTime().plusNanos(timeoutMs * 1_000_000L);
                if (now.isAfter(deadline)) {
                    handleStaleMedia(entity);
                    timedOut++;
                }
            }
            log.info("media gc finished. task=staleUpload processed={} timedOut={} failed=0 costMs={}",
                    uploadingMediaEntities.size(), timedOut, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("media gc failed. task=staleUpload costMs={}", System.currentTimeMillis() - start, e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new IllegalStateException(e);
        }
    }

    private void handleStaleMedia(MediaInfoEntity media) {
        // 主动查询 Storage 并流转状态
        MediaStatus currentStatus = mediaService.refreshMediaStatus(media.getMediaId());
        // 状态依然是 UPLOADING，说明 OSS 真的没有收到文件，此时标记为超时
        if (currentStatus.getStatus() == MediaStatusEnum.UPLOADING) {
            mediaProcessService.updateStatus(media.getMediaId(), new MediaStatus(MediaStatusEnum.TRANSFER_TIMEOUT));
        }
    }

    /**
     * 根据文件大小动态计算上传超时阈值（毫秒）。
     */
    private long calculateTimeoutMs(Long size) {
        if (size == null || size <= 0) {
            return mediaProperties.getBaseTimeoutMs();
        }
        long sizeBasedMs = size * 1000L / mediaProperties.getAssumedSpeedBps();
        return Math.max(mediaProperties.getBaseTimeoutMs(),
                Math.min(mediaProperties.getMaxTimeoutMs(), sizeBasedMs));
    }
}