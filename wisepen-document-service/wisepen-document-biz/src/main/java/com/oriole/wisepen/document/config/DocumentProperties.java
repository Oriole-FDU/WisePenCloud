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

    /**
     * 文档转换 provider：onlyoffice 或 legacy
     */
    private String conversionProvider = "onlyoffice";

    /**
     * ONLYOFFICE Document Server 内网地址，供后端调用转换 API
     */
    private String onlyofficeInternalUrl = "http://localhost:8101/";

    /**
     * 文档服务可被 ONLYOFFICE 容器访问的回调基址
     */
    private String onlyofficeCallbackBaseUrl = "http://host.docker.internal:19906";

    public String getOnlyofficeInternalUrl() {
        return onlyofficeInternalUrl.endsWith("/") ? onlyofficeInternalUrl.substring(0, onlyofficeInternalUrl.length() - 1) : onlyofficeInternalUrl;
    }

    public String getOnlyofficeCallbackBaseUrl() {
        return onlyofficeCallbackBaseUrl.endsWith("/") ? onlyofficeCallbackBaseUrl.substring(0, onlyofficeCallbackBaseUrl.length() - 1) : onlyofficeCallbackBaseUrl;
    }

    /**
     * ONLYOFFICE JWT 密钥。生产环境必须通过配置中心覆盖
     */
    private String onlyofficeJwtSecret = "wisepen-onlyoffice-dev-secret";

    /**
     * ONLYOFFICE JWT 请求头
     */
    private String onlyofficeJwtHeader = "Authorization";

    /**
     * ONLYOFFICE JWT 请求头前缀
     */
    private String onlyofficeJwtPrefix = "Bearer ";

    /**
     * ONLYOFFICE 转换超时时间
     */
    private long onlyofficeConversionTimeoutMs = 120_000L;

    /**
     * ONLYOFFICE 异步转换轮询间隔
     */
    private long onlyofficeConversionPollIntervalMs = 1_000L;

    /**
     * 给 ONLYOFFICE 读取源文件的预签名 URL 有效期
     */
    private long onlyofficeSourceUrlDurationSeconds = 3_600L;

    /**
     * 文档下载时后端读取对象存储文件的预签名 URL 有效期
     */
    private long downloadUrlDurationSeconds = 900L;

    /**
     * ONLYOFFICE 编辑会话过期时间
     */
    private long onlyofficeEditSessionTtlSeconds = 86_400L;

    /**
     * 本地临时缓存目录（Stage 3 下载源文件 / 存储转换产物时使用）
     * e.g. /tmp/wisepen/document/cache/
     */
    private String cachePath = "/tmp/wisepen/document/cache/";

    /**
     * 暗水印 AES-128 密钥（Base64 编码，16 字节）。
     * 生产环境必须通过配置中心覆盖此默认值。
     * 默认值仅用于开发/测试，不得用于生产。
     */
    private String watermarkSecretKey = "d2lzZXBlbmRlZmF1bHQ="; // base64("wisependefault") - 14 chars, padded to 16 in codec

    /**
     * Stage 3 底部免责声明所用 CJK 常规字体文件路径（TTF/OTF，需包含所有声明汉字）。
     * PDFBox 将对实际使用的字符自动 subset 嵌入，通常仅增加约 60–80 KB 体积。
     * 若路径不存在或为空，中文声明行将静默跳过，仅保留英文声明。
     * 推荐字体：NotoSansSC-Regular.otf（开源，可从 Google Fonts 获取）。
     * e.g. /usr/share/fonts/opentype/noto/NotoSansSC-Regular.otf
     */
    private String cjkRegularFontPath = "";

    /**
     * Stage 3 底部免责声明所用 CJK 粗体字体文件路径（TTF/OTF）。
     * 用于渲染加粗红色警告行；若为空则回退到 cjkRegularFontPath。
     * e.g. /usr/share/fonts/opentype/noto/NotoSansSC-Bold.otf
     */
    private String cjkBoldFontPath = "";

    /**
     * 定时任务检测 UPLOADING 文档的执行间隔（毫秒），默认 5 分钟。
     */
    private long staleCheckDelayMs = 300_000L;

    /**
     * 上传超时计算：基础超时时长（毫秒），与文件大小无关的最低等待时间，默认 10 分钟。
     */
    private long baseTimeoutMs = 600_000L;

    /**
     * 上传超时计算：假设的最低上传速度（字节/秒），默认 100 KB/s。
     * timeout = max(baseTimeout, min(maxTimeout, expectedSize / assumedSpeedBps * 1000))
     */
    private long assumedSpeedBps = 102_400L;

    /**
     * 上传超时计算：单文档允许的最大超时时长（毫秒），默认 60 分钟。
     */
    private long maxTimeoutMs = 3_600_000L;
}
