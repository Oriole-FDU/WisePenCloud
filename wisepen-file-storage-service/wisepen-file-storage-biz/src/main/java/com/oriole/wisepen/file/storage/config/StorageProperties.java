package com.oriole.wisepen.file.storage.config;

import com.oriole.wisepen.file.storage.api.enums.StorageProviderEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 存储服务全局配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.storage")
public class StorageProperties {

    private Upload upload = new Upload();
    private Retention retention = new Retention();
    private Endpoint endpoint = new Endpoint();
    private Providers providers = new Providers();

    @Data
    public static class Upload {
        /** 上传凭证默认有效时长（秒） */
        private Long defaultTicketDuration = 900L;
        /** 小文件最大限制大小（字节） */
        private Long maxSmallFileSize = 5 * 1024 * 1024L;
        /** 上传中记录超时认定时间（小时） */
        private Integer uploadingTimeoutHours = 2;
    }

    @Data
    public static class Retention {
        /** 软删除文件回收站保留天数 */
        private Integer deletedRetentionDays = 30;
    }

    @Data
    public static class Endpoint {
        /** 外网能访问到本服务的接口根路径，用于 OSS 回调 */
        private String apiDomain;
    }

    @Data
    public static class Providers {
        /** 首要存储提供商编码 */
        private String primaryCode;
        /** 存储提供商编码到配置的映射 */
        private Map<String, Provider> items = new LinkedHashMap<>();
    }

    @Data
    public static class Provider {
        private String name;
        private StorageProviderEnum provider;
        private String region;
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucketName;
        private String domain;
        private String roleArn;
        private Boolean enabled = true;
    }
}