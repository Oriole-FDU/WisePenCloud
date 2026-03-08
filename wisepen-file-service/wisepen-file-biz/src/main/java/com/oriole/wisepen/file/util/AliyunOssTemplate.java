package com.oriole.wisepen.file.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.oriole.wisepen.file.config.FileProperties;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.exception.FileErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.util.Date;

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

    public String getPresignedUrl(String objectKey, int expirationMinutes) {
        FileProperties.OssConfig ossConfig = fileProperties.getOss();
        if (!ossConfig.isEnabled()) {
            throw new ServiceException("OSS is disabled");
        }
        try {
            ensureClient();
            Date expiration = new Date(System.currentTimeMillis() + expirationMinutes * 60 * 1000L);
            URL url = ossClient.generatePresignedUrl(ossConfig.getBucketName(), objectKey, expiration);
            return url.toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for Aliyun OSS", e);
            throw new ServiceException("获取下载链接失败: " + e.getMessage());
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
