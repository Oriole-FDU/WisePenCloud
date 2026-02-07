package com.oriole.wisepen.file.consumer;

import com.alibaba.fastjson2.JSON;
import com.oriole.wisepen.file.api.constant.FileConstants;
import com.oriole.wisepen.file.api.domain.dto.FileConvertTaskDTO;
import com.oriole.wisepen.file.api.domain.dto.FileUploadTaskDTO;
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

    @Override
    public void run(String... args) {
        new Thread(() -> {
            log.info("FileConvertConsumer started, listening to queue: {}", FileConstants.CONVERT_QUEUE_KEY);
            while (true) {
                try {
                    String taskJson = stringRedisTemplate.opsForList().rightPop(FileConstants.CONVERT_QUEUE_KEY, 5, TimeUnit.SECONDS);
                    
                    if (taskJson == null) {
                        continue;
                    }

                    log.info("Received conversion task: {}", taskJson);
                    FileConvertTaskDTO task = JSON.parseObject(taskJson, FileConvertTaskDTO.class);
                    processTask(task);

                } catch (Exception e) {
                    log.error("Error processing conversion task", e);
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "File-Convert-Consumer").start();
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

            // 生成最终 PDF 路径 (存放于模拟 OSS 目录)
            String uuId = java.util.UUID.randomUUID().toString();
            String finalPath = "/tmp/wisepen/upload/oss/" + uuId + ".pdf";
            
            // 缓存转换后的 PDF 到本地缓存目录
            String cachePdfPath = "/tmp/wisepen/upload/cache/" + uuId + ".pdf";
            File cachePdfFile = new File(cachePdfPath);
            cn.hutool.core.io.FileUtil.mkdir(cachePdfFile.getParentFile());
            cn.hutool.core.io.FileUtil.move(tempPdf, cachePdfFile, true);
            
            log.info("Conversion successful, PDF cached at {}. Pushing upload task...", cachePdfPath);

            // 推送 PDF 上传任务到 Redis 队列
            FileUploadTaskDTO uploadTask = FileUploadTaskDTO.builder()
                    .fileId(task.getFileId())
                    .originalFilename(task.getOriginalFilename())
                    .tempFilePath(cachePdfPath)
                    .targetPath(finalPath)
                    .md5(task.getMd5())
                    .isConvertedPdf(true)
                    .build();
            
            stringRedisTemplate.opsForList().leftPush(FileConstants.UPLOAD_QUEUE_KEY, JSON.toJSONString(uploadTask));
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
