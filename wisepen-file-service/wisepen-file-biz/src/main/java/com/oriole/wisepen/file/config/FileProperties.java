package com.oriole.wisepen.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件服务配置属性
 *
 * @author Ian.Xiong
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.file")
public class FileProperties {

    /**
     * 物理存储路径 (Consumer 使用)
     * e.g. /tmp/wisepen/upload/oss/
     */
    private String storagePath = "/tmp/wisepen/upload/oss/";

    /**
     * 本地缓存路径 (临时文件存储)
     * e.g. /tmp/wisepen/upload/cache/
     */
    private String cachePath = "/tmp/wisepen/upload/cache/";

    /**
     * 公网访问域名 (Web URL 使用)
     * e.g. http://localhost:9200/file/download/
     */
    private String domain = "http://localhost:9200/file/download/";

    /**
     * 实例 ID，用于区分不同服务实例的 Redis 队列
     * 避免多实例部署时，A 实例上传的文件（本地缓存）被 B 实例的 Consumer 抢占处理导致 FileNotFound
     * 默认生成随机 UUID (已移除，需在配置中指定)
     */
    private String instanceId;

    /**
     * OSS 配置
     */
    private OssConfig oss = new OssConfig();

    @Data
    public static class OssConfig {
        /**
         * 是否启用 OSS 上传
         */
        private boolean enabled = false;

        /**
         * OSS Endpoint (例如: oss-cn-hangzhou.aliyuncs.com)
         */
        private String endpoint;

        /**
         * Access Key ID
         */
        private String accessKeyId;

        /**
         * Access Key Secret
         */
        private String accessKeySecret;

        /**
         * Bucket Name
         */
        private String bucketName;
    }
}
