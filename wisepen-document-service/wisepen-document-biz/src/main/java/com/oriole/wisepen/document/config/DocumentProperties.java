package com.oriole.wisepen.document.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档服务配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.document")
public class DocumentProperties {

    private Integration integration = new Integration();
    private ConversionRuntime conversionRuntime = new ConversionRuntime();
    private Download download = new Download();
    private EditSession editSession = new EditSession();
    private Watermark watermark = new Watermark();
    private LocalResources localResources = new LocalResources();
    private UploadTimeout uploadTimeout = new UploadTimeout();

    @Data
    public static class Integration {
        /** 文档转换 provider：onlyoffice 或 legacy */
        private String provider = "onlyoffice";
        private OnlyOffice onlyoffice = new OnlyOffice();
    }

    @Data
    public static class OnlyOffice {
        /** ONLYOFFICE Document Server 内网地址，供后端调用转换 API */
        private String internalUrl = "http://localhost:8101/";
        /** 文档服务可被 ONLYOFFICE 容器访问的回调基址 */
        private String callbackBaseUrl = "http://host.docker.internal:19906";
        /** ONLYOFFICE JWT 密钥 */
        private String jwtSecret = "wisepen-onlyoffice-dev-secret";
        /** ONLYOFFICE JWT 请求头 */
        private String jwtHeader = "Authorization";
        /** ONLYOFFICE JWT 请求头前缀 */
        private String jwtPrefix = "Bearer ";

        public String getInternalUrl() {
            return internalUrl.endsWith("/") ? internalUrl.substring(0, internalUrl.length() - 1) : internalUrl;
        }

        public String getCallbackBaseUrl() {
            return callbackBaseUrl.endsWith("/") ? callbackBaseUrl.substring(0, callbackBaseUrl.length() - 1) : callbackBaseUrl;
        }
    }

    @Data
    public static class ConversionRuntime {
        /** ONLYOFFICE 转换超时时间 */
        private long timeoutMs = 120_000L;
        /** ONLYOFFICE 异步转换轮询间隔 */
        private long pollIntervalMs = 1_000L;
        /** 给 ONLYOFFICE 读取源文件的预签名 URL 有效期 */
        private long sourceUrlDurationSeconds = 3_600L;
    }

    @Data
    public static class Download {
        /** 文档下载时后端读取对象存储文件的预签名 URL 有效期 */
        private long urlDurationSeconds = 900L;
    }

    @Data
    public static class EditSession {
        /** ONLYOFFICE 编辑会话过期时间 */
        private long ttlSeconds = 86_400L;
    }

    @Data
    public static class Watermark {
        /** 暗水印 AES-128 密钥（Base64 编码，16 字节） */
        private String secretKey = "d2lzZXBlbmRlZmF1bHQ=";
        /** 水印与免责声明渲染使用的字体 */
        private Fonts fonts = new Fonts();
    }

    @Data
    public static class Fonts {
        /** CJK 常规字体文件路径 */
        private String regularPath = "";
        /** CJK 粗体字体文件路径 */
        private String boldPath = "";
    }

    @Data
    public static class LocalResources {
        /** 本地临时缓存目录 */
        private String cachePath = "/tmp/wisepen/document/cache/";
    }

    @Data
    public static class UploadTimeout {
        /** 与文件大小无关的最低等待时间 */
        private long baseTimeoutMs = 600_000L;
        /** 假设的最低上传速度（字节/秒） */
        private long assumedSpeedBps = 102_400L;
        /** 单文档允许的最大超时时长 */
        private long maxTimeoutMs = 3_600_000L;
    }
}
