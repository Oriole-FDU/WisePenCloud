package com.oriole.wisepen.generic.resource.task;

import com.oriole.wisepen.generic.resource.api.domain.base.GenericResourceStatus;
import com.oriole.wisepen.generic.resource.api.enums.GenericResourceStatusEnum;
import com.oriole.wisepen.generic.resource.config.GenericResourceProperties;
import com.oriole.wisepen.generic.resource.domain.entity.GenericResourceInfoEntity;
import com.oriole.wisepen.generic.resource.repository.GenericResourceInfoRepository;
import com.oriole.wisepen.generic.resource.service.IGenericResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通用资源服务专属垃圾回收器 (Garbage Collector)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenericResourceGcTask {

    private final GenericResourceInfoRepository genericResourceInfoRepository;
    private final IGenericResourceService genericResourceService;
    private final GenericResourceProperties genericResourceProperties;

    @Scheduled(fixedDelayString = "${wisepen.generic-resource.stale-check-delay-ms:300000}")
    public void detectStaleUploads() {
        long start = System.currentTimeMillis();
        log.info("generic resource gc started. task=staleUpload");
        try {
            List<GenericResourceInfoEntity> uploadingGenericResourceEntities =
                    genericResourceInfoRepository.findByStatus(GenericResourceStatusEnum.UPLOADING);
            if (uploadingGenericResourceEntities == null || uploadingGenericResourceEntities.isEmpty()) {
                log.info("generic resource gc finished. task=staleUpload processed=0 timedOut=0 failed=0 costMs={}",
                        System.currentTimeMillis() - start);
                return;
            }
            log.debug("generic resource gc candidates found. task=staleUpload pending={}", uploadingGenericResourceEntities.size());
            LocalDateTime now = LocalDateTime.now();
            int timedOut = 0;
            for (GenericResourceInfoEntity entity : uploadingGenericResourceEntities) {
                Long size = entity.getSize();
                long timeoutMs = calculateTimeoutMs(size);
                LocalDateTime deadline = entity.getCreateTime().plusNanos(timeoutMs * 1_000_000L);
                if (now.isAfter(deadline)) {
                    handleStaleGenericResource(entity);
                    timedOut++;
                }
            }
            log.info("generic resource gc finished. task=staleUpload processed={} timedOut={} failed=0 costMs={}",
                    uploadingGenericResourceEntities.size(), timedOut, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("generic resource gc failed. task=staleUpload costMs={}", System.currentTimeMillis() - start, e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new IllegalStateException(e);
        }
    }

    private void handleStaleGenericResource(GenericResourceInfoEntity entity) {
        GenericResourceStatus currentStatus = genericResourceService.refreshGenericResourceStatus(
                entity.getGenericResourceId(), entity.getUploaderId());
        if (currentStatus.getStatus() == GenericResourceStatusEnum.UPLOADING) {
            genericResourceService.updateStatus(
                    entity.getGenericResourceId(), new GenericResourceStatus(GenericResourceStatusEnum.TRANSFER_TIMEOUT));
        }
    }

    /**
     * 根据文件大小动态计算上传超时阈值（毫秒）。
     */
    private long calculateTimeoutMs(Long size) {
        if (size == null || size <= 0) {
            return genericResourceProperties.getBaseTimeoutMs();
        }
        long sizeBasedMs = size * 1000L / genericResourceProperties.getAssumedSpeedBps();
        return Math.max(genericResourceProperties.getBaseTimeoutMs(),
                Math.min(genericResourceProperties.getMaxTimeoutMs(), sizeBasedMs));
    }
}
