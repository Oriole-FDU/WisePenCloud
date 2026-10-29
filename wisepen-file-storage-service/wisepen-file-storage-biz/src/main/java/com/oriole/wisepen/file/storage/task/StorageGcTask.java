package com.oriole.wisepen.file.storage.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageStatusEnum;
import com.oriole.wisepen.file.storage.config.StorageProperties;
import com.oriole.wisepen.file.storage.domain.entity.StorageRecordEntity;
import com.oriole.wisepen.file.storage.strategy.StorageManager;
import com.oriole.wisepen.file.storage.mapper.StorageRecordMapper;
import com.oriole.wisepen.file.storage.service.IStorageService;
import com.oriole.wisepen.file.storage.strategy.StorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 存储服务专属垃圾回收器 (Garbage Collector)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageGcTask {

    private final IStorageService storageService;
    private final StorageRecordMapper storageRecordMapper;
    private final StorageManager storageManager;
    private final StorageProperties storageProperties;

    /**
     * 任务：死信占位符清理 (默认每2小时执行一次)
     * 用户申请了上传凭证，但一直没有回调，主动去云端查证一次，有文件则自愈补偿，无文件直接删除记录。
     */
    @Scheduled(cron = "${wisepen.storage.cleanup.zombie-cron:0 0 */2 * * ?}")
    public void cleanupUploadingZombies() {
        long start = System.currentTimeMillis();
        log.info("storage gc started. task=uploadingZombie");
        LocalDateTime threshold = LocalDateTime.now().minusHours(storageProperties.getUpload().getUploadingTimeoutHours());

        // 每次最多处理 500 条，防止内存溢出和数据库长事务
        List<StorageRecordEntity> zombies = storageRecordMapper.selectList(
                Wrappers.<StorageRecordEntity>lambdaQuery()
                        .eq(StorageRecordEntity::getStatus, StorageStatusEnum.UPLOADING)
                        .le(StorageRecordEntity::getCreateTime, threshold)
                        .last("LIMIT 500")
        );

        if (zombies.isEmpty()) {
            log.info("storage gc finished. task=uploadingZombie processed=0 recovered=0 deleted=0 failed=0 costMs={}",
                    System.currentTimeMillis() - start);
            return;
        }

        int recoveredCount = 0;
        int deletedCount = 0;
        int failedCount = 0;

        for (StorageRecordEntity zombie : zombies) {
            try {
                StorageRecordDTO recoveredDto = storageService.compensateStatus(zombie);
                if (recoveredDto != null) {
                    recoveredCount++;
                } else {
                    // 云端确实没有，物理抹除占位符
                    storageRecordMapper.deleteById(zombie.getFileId());
                    deletedCount++;
                }
            } catch (Exception e) {
                // 单条失败不能影响后续遍历
                failedCount++;
                log.warn("storage zombie recover failed. objectKey={}", zombie.getObjectKey(), e);
            }
        }
        log.info("storage gc finished. task=uploadingZombie processed={} recovered={} deleted={} failed={} costMs={}",
                zombies.size(), recoveredCount, deletedCount, failedCount, System.currentTimeMillis() - start);
    }

    /**
     * 任务：回收站物理垃圾回收 (默认每天凌晨 3 点执行)
     * 业务场景：将那些被业务端软删除 (DELETED) 且已经度过 30 天（默认）后悔期的文件，从物理云盘彻底抹除。
     */
    @Scheduled(cron = "${wisepen.storage.cleanup.physical-gc-cron:0 0 3 * * ?}")
    public void physicalGarbageCollection() {
        long start = System.currentTimeMillis();
        log.info("storage gc started. task=physicalDelete");
        LocalDateTime threshold = LocalDateTime.now().minusDays(storageProperties.getRetention().getDeletedRetentionDays());

        List<StorageRecordEntity> trashes = storageRecordMapper.selectList(
                Wrappers.<StorageRecordEntity>lambdaQuery()
                        .eq(StorageRecordEntity::getStatus, StorageStatusEnum.DELETED)
                        // 注意：这里用 update_time，因为软删除触发的是 update
                        .le(StorageRecordEntity::getUpdateTime, threshold)
                        .last("LIMIT 1000")
        );

        if (trashes.isEmpty()) {
            log.info("storage gc finished. task=physicalDelete processed=0 deleted=0 failed=0 costMs={}",
                    System.currentTimeMillis() - start);
            return;
        }

        int successCount = 0;
        int failedCount = 0;
        for (StorageRecordEntity trash : trashes) {
            try {
                // 先删云端物理文件
                StorageProvider provider = storageManager.getProvider(trash.getProviderCode());
                provider.deleteObject(trash.getObjectKey());

                // 物理删除不抛异常，再硬删除数据库记录
                storageRecordMapper.deleteById(trash.getFileId());
                successCount++;
            } catch (Exception e) {
                failedCount++;
                log.warn("storage physical delete failed. objectKey={}", trash.getObjectKey(), e);
            }
        }
        log.info("storage gc finished. task=physicalDelete processed={} deleted={} failed={} costMs={}",
                trashes.size(), successCount, failedCount, System.currentTimeMillis() - start);
    }
}
