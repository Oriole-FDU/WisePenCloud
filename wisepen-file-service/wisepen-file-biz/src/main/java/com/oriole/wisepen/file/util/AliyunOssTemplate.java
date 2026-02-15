package com.oriole.wisepen.file.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.oriole.wisepen.file.config.FileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Aliyun OSS 操作工具类
 *
 * @author Ian.Xiong
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
            // Lazy initialization or check if client is null/shutdown
            if (ossClient == null) {
                ossClient = new OSSClientBuilder().build(
                        ossConfig.getEndpoint(),
                        ossConfig.getAccessKeyId(),
                        ossConfig.getAccessKeySecret());
            }

            log.info("Uploading file to Aliyun OSS: bucket={}, key={}", ossConfig.getBucketName(), objectKey);
            ossClient.putObject(ossConfig.getBucketName(), objectKey, file);
            log.info("Aliyun OSS upload successful: {}", objectKey);

        } catch (Exception e) {
            log.error("Failed to upload file to Aliyun OSS", e);
            throw new RuntimeException("Aliyun OSS upload failed", e);
        }
    }
    
    // Note: In a real production scenario, you might want to manage the OSSClient lifecycle more carefully 
    // (e.g., using a Bean with @PreDestroy to shutdown), or use a pool if high concurrency.
    // For this implementation, we keep it simple.
}
