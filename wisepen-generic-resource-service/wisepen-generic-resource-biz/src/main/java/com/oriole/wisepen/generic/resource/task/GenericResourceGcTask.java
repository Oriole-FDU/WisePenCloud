package com.oriole.wisepen.generic.resource.task;

import com.oriole.wisepen.generic.resource.api.domain.base.GenericResourceStatus;
import com.oriole.wisepen.generic.resource.api.enums.GenericResourceStatusEnum;
import com.oriole.wisepen.generic.resource.config.GenericResourceProperties;
import com.oriole.wisepen.generic.resource.domain.entity.GenericResourceInfoEntity;
import com.oriole.wisepen.generic.resource.repository.GenericResourceInfoRepository;
import com.oriole.wisepen.generic.resource.service.IGenericResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    @Scheduled(fixedDelayString = "${wisepen.generic-resource.stale-check.delay-ms:300000}")
    public void detectStaleUploads() {
        long start = System.currentTimeMillis();
        log.info("generic resource gc started. task=staleUpload");
        int processed = 0;
        int timedOut = 0;
        int recovered = 0;
        int failed = 0;
        try {
            int batchSize = genericResourceProperties.getStaleCheck().getBatchSize();
            LocalDateTime now = LocalDateTime.now();
            // 每轮限制上传检查和注册补偿各自的候选数量，避免积压时无限串行调用远程服务。
            List<GenericResourceInfoEntity> uploadingGenericResourceEntities =
                    genericResourceInfoRepository.findByStatus(GenericResourceStatusEnum.UPLOADING,
                            PageRequest.of(0, batchSize, Sort.by("createTime", "_id")));
            for (GenericResourceInfoEntity entity : uploadingGenericResourceEntities) {
                processed++;
                try {
                    long timeoutMs = calculateTimeoutMs(entity.getSize());
                    LocalDateTime deadline = entity.getCreateTime().plusNanos(timeoutMs * 1_000_000L);
                    if (now.isAfter(deadline)) {
                        GenericResourceStatus currentStatus = genericResourceService.refreshGenericResourceStatus(
                                entity.getGenericResourceId(), entity.getUploaderId());
                        if (currentStatus.getStatus() == GenericResourceStatusEnum.UPLOADING
                                && genericResourceInfoRepository.updateStatusIfCurrent(entity.getGenericResourceId(),
                                GenericResourceStatusEnum.UPLOADING, GenericResourceStatusEnum.TRANSFER_TIMEOUT) > 0) {
                            timedOut++;
                            log.debug("generic resource upload timed out. genericResourceId={} from={} to={}",
                                    entity.getGenericResourceId(), GenericResourceStatusEnum.UPLOADING, GenericResourceStatusEnum.TRANSFER_TIMEOUT);
                        } else if (currentStatus.getStatus() == GenericResourceStatusEnum.READY) {
                            recovered++;
                        }
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("generic resource gc upload candidate failed. genericResourceId={}", entity.getGenericResourceId(), e);
                }
            }

            LocalDateTime registrationCutoff = now.minusNanos(
                    genericResourceProperties.getStaleCheck().getRegistrationTimeoutMs() * 1_000_000L);
            List<GenericResourceInfoEntity> registrations = genericResourceInfoRepository.findStaleByStatusIn(
                    List.of(GenericResourceStatusEnum.REGISTERING_RES, GenericResourceStatusEnum.REGISTERING_RES_TIMEOUT),
                    registrationCutoff, PageRequest.of(0, batchSize, Sort.by("updateTime", "_id")));
            for (GenericResourceInfoEntity entity : registrations) {
                processed++;
                try {
                    if (entity.getStatus() == GenericResourceStatusEnum.REGISTERING_RES) {
                        if (genericResourceInfoRepository.expireRegistration(entity.getGenericResourceId(), registrationCutoff) == 0) {
                            continue;
                        }
                        log.debug("generic resource registration expired. genericResourceId={} from={} to={}",
                                entity.getGenericResourceId(), GenericResourceStatusEnum.REGISTERING_RES, GenericResourceStatusEnum.REGISTERING_RES_TIMEOUT);
                    }
                    GenericResourceStatus currentStatus = genericResourceService.refreshGenericResourceStatus(
                            entity.getGenericResourceId(), entity.getUploaderId());
                    if (currentStatus.getStatus() == GenericResourceStatusEnum.READY) {
                        recovered++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("generic resource gc registration candidate failed. genericResourceId={}", entity.getGenericResourceId(), e);
                }
            }
        } catch (Exception e) {
            failed++;
            log.error("generic resource gc aborted. task=staleUpload costMs={}", System.currentTimeMillis() - start, e);
        }
        log.info("generic resource gc finished. task=staleUpload processed={} timedOut={} recovered={} failed={} costMs={}",
                processed, timedOut, recovered, failed, System.currentTimeMillis() - start);
    }

    /**
     * 根据文件大小动态计算上传超时阈值（毫秒）。
     */
    private long calculateTimeoutMs(Long size) {
        if (size == null || size <= 0) {
            return genericResourceProperties.getUploadTimeout().getBaseTimeoutMs();
        }
        long sizeBasedMs = size * 1000L / genericResourceProperties.getUploadTimeout().getAssumedSpeedBps();
        return Math.max(genericResourceProperties.getUploadTimeout().getBaseTimeoutMs(),
                Math.min(genericResourceProperties.getUploadTimeout().getMaxTimeoutMs(), sizeBasedMs));
    }
}
