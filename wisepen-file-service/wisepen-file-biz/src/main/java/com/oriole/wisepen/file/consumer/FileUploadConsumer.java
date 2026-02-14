package com.oriole.wisepen.file.consumer;

import com.alibaba.fastjson2.JSON;
import com.oriole.wisepen.file.api.constant.FileConstants;
import com.oriole.wisepen.file.api.domain.dto.FileUploadTaskDTO;
import com.oriole.wisepen.file.domain.entity.FileInfo;
import com.oriole.wisepen.file.mapper.FileMapper;
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
 * @author Ian.Xiong
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadConsumer implements CommandLineRunner {

    private final StringRedisTemplate stringRedisTemplate;
    private final FileMapper fileMapper;

    @Override
    public void run(String... args) {
        new Thread(() -> {
            log.info("FileUploadConsumer started, listening to queue: {}", FileConstants.UPLOAD_QUEUE_KEY);
            while (true) {
                try {
                    String taskJson = stringRedisTemplate.opsForList().rightPop(FileConstants.UPLOAD_QUEUE_KEY, 5, TimeUnit.SECONDS);

                    if (taskJson == null) {
                        continue;
                    }

                    log.info("Received upload task: {}", taskJson);
                    FileUploadTaskDTO task = JSON.parseObject(taskJson, FileUploadTaskDTO.class);
                    processTask(task);

                } catch (Exception e) {
                    log.error("Error processing upload task", e);
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "File-Upload-Consumer").start();
    }

    private void processTask(FileUploadTaskDTO task) {
        File cacheFile = new File(task.getTempFilePath());
        if (!cacheFile.exists()) {
            log.error("Cache file not found for upload: {}", cacheFile.getAbsolutePath());
            return;
        }

        try {
            File targetFile = new File(task.getTargetPath());

            // 模拟 OSS，需要物理路径存在
            cn.hutool.core.io.FileUtil.mkdir(targetFile.getParentFile());

            // 上传文件到"OSS"路径
            cn.hutool.core.io.FileUtil.move(cacheFile, targetFile, true);

            log.info("File uploaded to simulated OSS: {}", task.getTargetPath());

            // 更新数据库状态
            FileInfo fileInfo = fileMapper.selectById(task.getFileId());
            if (fileInfo == null) {
                log.error("FileInfo not found for fileId: {}", task.getFileId());
                return;
            }

            FileInfo update = new FileInfo();
            update.setId(task.getFileId());
            update.setUpdateTime(java.time.LocalDateTime.now());

            if (Boolean.TRUE.equals(task.getIsConvertedPdf())) {
                // 转换后的 PDF 上传完成 → url + pdfUrl + AVAILABLE
                update.setPdfUrl(task.getTargetPath());
                update.setStatus(FileConstants.UPLOAD_STATUS_AVAILABLE);

            } else if (Boolean.TRUE.equals(task.getIsPdfDirect())) {
                // PDF 直传：url 和 pdfUrl 写同一地址，直接 AVAILABLE
                update.setUrl(task.getTargetPath());
                update.setPdfUrl(task.getTargetPath());
                update.setStatus(FileConstants.UPLOAD_STATUS_AVAILABLE);

            } else {
                // 原始文件上传完成
                update.setUrl(task.getTargetPath());
                // 非 Office 文档直接可用；Office 文档需等待转换完成
                if (!isOfficeDocument(fileInfo.getType())) {
                    update.setStatus(FileConstants.UPLOAD_STATUS_AVAILABLE);
                }
            }

            fileMapper.updateById(update);

        } catch (Exception e) {
            log.error("Upload failed for fileId: {}", task.getFileId(), e);
        }
    }

    private boolean isOfficeDocument(String extension) {
        if (extension == null) {
            return false;
        }
        return FileConstants.OFFICE_EXTENSIONS.contains(extension.toLowerCase());
    }
}
