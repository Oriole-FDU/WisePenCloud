package com.oriole.wisepen.file.consumer;

import com.alibaba.fastjson2.JSON;
import com.oriole.wisepen.file.api.constant.FileConstants;
import com.oriole.wisepen.file.api.domain.dto.FileUploadTaskDTO;
import com.oriole.wisepen.file.config.FileProperties;
import com.oriole.wisepen.file.domain.entity.FileInfo;
import com.oriole.wisepen.file.mapper.FileMapper;
import com.oriole.wisepen.file.service.FileAvailabilityService;
import com.oriole.wisepen.file.util.AliyunOssTemplate;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * 文件上传消费者 - 负责将本地缓存文件同步到模拟 OSS 路径
 *
 * @author Ian.xiong
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadConsumer implements CommandLineRunner {

    private final StringRedisTemplate stringRedisTemplate;
    private final FileMapper fileMapper;
    private final FileProperties fileProperties;
    private final AliyunOssTemplate aliyunOssTemplate;
    private final FileAvailabilityService fileAvailabilityService;

    private static final long POP_TIMEOUT_SECONDS = 5L;
    private static final long ERROR_RETRY_DELAY_SECONDS = 1L;

    private volatile boolean isRunning = true;
    private final java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "File-Upload-Consumer"));

    @Override
    public void run(String... args) {
        executorService.submit(() -> {
            String queueKey = FileConstants.UPLOAD_QUEUE_KEY + ":" + fileProperties.getInstanceId();
            log.info("FileUploadConsumer started, listening to instance-specific queue: {}", queueKey);
            while (isRunning) {
                try {
                    String taskJson = stringRedisTemplate.opsForList().rightPop(queueKey, POP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    if (taskJson == null) {
                        continue;
                    }

                    log.info("Received upload task: {}", taskJson);
                    FileUploadTaskDTO task = JSON.parseObject(taskJson, FileUploadTaskDTO.class);
                    processTask(task);

                } catch (Exception e) {
                    log.error("Error processing upload task", e);
                    try {
                        TimeUnit.SECONDS.sleep(ERROR_RETRY_DELAY_SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("FileUploadConsumer stopped.");
        });
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down FileUploadConsumer...");
        isRunning = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void processTask(FileUploadTaskDTO task) {
        File cacheFile = new File(task.getTempFilePath());
        if (!cacheFile.exists()) {
            log.error("Cache file not found for upload: {}", cacheFile.getAbsolutePath());
            return;
        }

        try {
            String storagePath = fileProperties.getStoragePath();
            if (!storagePath.endsWith("/")) {
                storagePath += "/";
            }
            String objectKey = task.getTargetPath().replace(storagePath, "");
            if (objectKey.startsWith("/")) {
                objectKey = objectKey.substring(1);
            }

            if (fileProperties.getOss().isEnabled()) {
                aliyunOssTemplate.uploadFile(cacheFile, objectKey);
                log.info("File uploaded to Aliyun OSS: {}", objectKey);
            } else {
                File targetFile = new File(task.getTargetPath());
                cn.hutool.core.io.FileUtil.mkdir(targetFile.getParentFile());
                cn.hutool.core.io.FileUtil.copy(cacheFile, targetFile, true);
                log.info("File uploaded to simulated OSS: {}", task.getTargetPath());
            }

            FileInfo update = new FileInfo();
            update.setId(task.getFileId());
            update.setUpdateTime(java.time.LocalDateTime.now());

            // 构造资源注册所需的 fileInfo 内存快照 (避免查库)
            FileInfo snapshot = new FileInfo();
            snapshot.setId(task.getFileId());
            snapshot.setFilename(task.getOriginalFilename());
            snapshot.setSize(task.getSize());
            snapshot.setCreateBy(task.getCreateBy());
            snapshot.setType(cn.hutool.core.io.FileUtil.extName(task.getOriginalFilename()));

            if (Boolean.TRUE.equals(task.getIsConvertedPdf())) {
                // PDF 副本转换完成：仅补充 pdfUrl，不更改状态（原件上传时已设为 AVAILABLE）
                String finalPdfUrl = (task.getAccessUrl() != null && !task.getAccessUrl().isEmpty())
                        ? task.getAccessUrl() : task.getTargetPath();
                update.setPdfUrl(finalPdfUrl);
                fileMapper.updateById(update);

            } else if (Boolean.TRUE.equals(task.getIsPdfDirect())) {
                // PDF 原件直传：设为 AVAILABLE 并注册资源
                String finalPdfUrl = (task.getAccessUrl() != null && !task.getAccessUrl().isEmpty())
                        ? task.getAccessUrl() : task.getTargetPath();
                update.setPdfUrl(finalPdfUrl);
                fileAvailabilityService.markAvailableAndRegister(update, snapshot);

            } else {
                // 非 PDF 原始文件（含 Office 原件）：统一触发 markAvailableAndRegister
                // Office 文件虽仍需转换 PDF，但原件已可注册进资源系统，状态由 isConvertedPdf 回调更新
                fileAvailabilityService.markAvailableAndRegister(update, snapshot);
            }

        } catch (Exception e) {
            log.error("Upload failed for fileId: {}", task.getFileId(), e);
        }
    }
}
