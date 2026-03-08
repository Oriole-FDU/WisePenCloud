package com.oriole.wisepen.file.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.oriole.wisepen.file.config.FileProperties;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.exception.FileErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Aliyun OSS 操作工具类
 *
 * @author Ian.xiong
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunOssTemplate {

    private final FileProperties fileProperties;
    private OSS ossClient;

    public void uploadFile(File file, String objectKey) {
        FileProperties.OssConfig ossConfig = fileProperties.getOss();
        if (!ossConfig.isEnabled()) {
            log.warn("OSS is disabled, skip upload.");
            return;
        }

        try {
            ensureClient();
            log.info("Uploading file to Aliyun OSS: bucket={}, key={}", ossConfig.getBucketName(), objectKey);
            ossClient.putObject(ossConfig.getBucketName(), objectKey, file);
            log.info("Aliyun OSS upload successful: {}", objectKey);

        } catch (Exception e) {
            log.error("Failed to upload file to Aliyun OSS", e);
            throw new ServiceException(FileErrorCode.FILE_UPLOAD_ERROR);
        }
    }
    
    private synchronized void ensureClient() {
        if (ossClient == null) {
            FileProperties.OssConfig ossConfig = fileProperties.getOss();
            ossClient = new OSSClientBuilder().build(
                    ossConfig.getEndpoint(),
                    ossConfig.getAccessKeyId(),
                    ossConfig.getAccessKeySecret());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (ossClient != null) {
            log.info("Shutting down Aliyun OSS Client...");
                ossClient.shutdown();
        }
    }
}
