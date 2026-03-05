package com.oriole.wisepen.file.consumer;

import com.alibaba.fastjson2.JSON;
import com.oriole.wisepen.file.api.constant.FileConstants;
import com.oriole.wisepen.file.api.domain.dto.FileConvertTaskDTO;
import com.oriole.wisepen.file.api.domain.dto.FileUploadTaskDTO;
import com.oriole.wisepen.file.config.FileProperties;
import com.oriole.wisepen.file.domain.entity.FileInfo;
import com.oriole.wisepen.file.mapper.FileMapper;
import com.oriole.wisepen.file.service.OfficeConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * 文件转换任务消费者 - 处理文档转 PDF
 *
 * @author Ian.Xiong
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileConvertConsumer implements CommandLineRunner {

    private final StringRedisTemplate stringRedisTemplate;
    private final OfficeConversionService officeConversionService;
    private final FileMapper fileMapper;
    private final FileProperties fileProperties;

    private static final long POP_TIMEOUT_SECONDS = 5L;
    private static final long ERROR_RETRY_DELAY_SECONDS = 1L;

    private volatile boolean isRunning = true;
    private final java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "File-Convert-Consumer"));

    @Override
    public void run(String... args) {
        executorService.submit(() -> {
            String queueKey = FileConstants.CONVERT_QUEUE_KEY + ":" + fileProperties.getInstanceId();
            log.info("FileConvertConsumer started, listening to instance-specific queue: {}", queueKey);
            while (isRunning) {
                try {
                    String taskJson = stringRedisTemplate.opsForList().rightPop(queueKey, POP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    
                    if (taskJson == null) {
                        continue;
                    }

                    log.info("Received conversion task: {}", taskJson);
                    FileConvertTaskDTO task = JSON.parseObject(taskJson, FileConvertTaskDTO.class);
                    processTask(task);

                } catch (Exception e) {
                    log.error("Error processing conversion task", e);
                    try {
                        TimeUnit.SECONDS.sleep(ERROR_RETRY_DELAY_SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("FileConvertConsumer stopped.");
        });
    }

    @jakarta.annotation.PreDestroy
    public void destroy() {
        log.info("Shutting down FileConvertConsumer...");
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

    private void processTask(FileConvertTaskDTO task) {
        File rawFile = new File(task.getTempFilePath());
        if (!rawFile.exists()) {
            log.error("Raw file not found: {}", rawFile.getAbsolutePath());
            updateFailureStatus(task.getFileId());
            return;
        }

        File tempPdf = null;
        try {
            tempPdf = File.createTempFile("wisepen_convert_", ".pdf");
            
            log.info("Converting file {} to PDF...", task.getOriginalFilename());
            officeConversionService.convertToPdf(rawFile, tempPdf);

            // 生成 ObjectKey: yyyy/MM/dd/{uuid}.pdf (与 FileService 保持一致)
            String uuId = java.util.UUID.randomUUID().toString();
            String datePath = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd").format(java.time.LocalDateTime.now());


            String objectKey = datePath + "/" + uuId + ".pdf";

            // 物理存储路径 (模拟 OSS)
            String storagePath = fileProperties.getStoragePath();
            if (!storagePath.endsWith("/")) {
                storagePath += "/";
            }
            String finalPath = storagePath + objectKey;

            // 公网访问 URL
            String domain = fileProperties.getDomain();
            if (!domain.endsWith("/")) {
                domain += "/";
            }
            String pdfWebUrl = domain + objectKey;

            // 缓存转换后的 PDF 到本地缓存目录 (用于后续上传步骤)
            String cacheDir = fileProperties.getCachePath();
            if (!cacheDir.endsWith("/")) {
                cacheDir += "/";
            }
            String cachePdfPath = cacheDir + uuId + ".pdf";
            File cachePdfFile = new File(cachePdfPath);
            cn.hutool.core.io.FileUtil.mkdir(cachePdfFile.getParentFile());
            cn.hutool.core.io.FileUtil.move(tempPdf, cachePdfFile, true);
            
            log.info("Conversion successful, PDF cached at {}. Pushing upload task...", cachePdfPath);

            // 推送 PDF 上传任务到 Redis 队列 (必须推送到同实例的 Upload Queue)
            // 注意：因为 cachePdfPath 是本地路径，所以必须由本机消费
            FileUploadTaskDTO uploadTask = new FileUploadTaskDTO();
            cn.hutool.core.bean.BeanUtil.copyProperties(task, uploadTask);
            uploadTask.setTempFilePath(cachePdfPath);
            uploadTask.setTargetPath(finalPath);
            uploadTask.setAccessUrl(pdfWebUrl); // 传递 Web URL
            uploadTask.setIsConvertedPdf(true);
            
            String uploadQueueKey = FileConstants.UPLOAD_QUEUE_KEY + ":" + fileProperties.getInstanceId();
            stringRedisTemplate.opsForList().leftPush(uploadQueueKey, JSON.toJSONString(uploadTask));
            log.info("Pushed PDF upload task for fileId: {}", task.getFileId());

        } catch (Exception e) {
            log.error("Conversion failed for fileId: {}", task.getFileId(), e);
            updateFailureStatus(task.getFileId());
        } finally {
            if (tempPdf != null) {
                cn.hutool.core.io.FileUtil.del(tempPdf);
            }
        }
    }

    private void updateFailureStatus(Long fileId) {
        FileInfo update = new FileInfo();
        update.setId(fileId);
        update.setStatus(FileConstants.UPLOAD_STATUS_FAILED);
        update.setUpdateTime(java.time.LocalDateTime.now());
        fileMapper.updateById(update);
    }
}
