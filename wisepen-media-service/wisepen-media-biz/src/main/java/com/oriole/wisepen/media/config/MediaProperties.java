package com.oriole.wisepen.media.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 媒体服务配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.media")
public class MediaProperties {

    private LocalResources localResources = new LocalResources();
    private UploadTimeout uploadTimeout = new UploadTimeout();
    private ProcessingRuntime processingRuntime = new ProcessingRuntime();
    private Playback playback = new Playback();
    private StaleCheck staleCheck = new StaleCheck();

    @Data
    public static class LocalResources {
        /** 本地媒体处理缓存目录。 */
        private String cachePath = "/tmp/wisepen/media/cache/";
        /** FFmpeg 可执行文件路径。 */
        private String ffmpegPath = "ffmpeg";
        /** FFprobe 可执行文件路径。 */
        private String ffprobePath = "ffprobe";
    }

    @Data
    public static class UploadTimeout {
        /** 与文件大小无关的最低等待时间（毫秒）。 */
        private long baseTimeoutMs = 600_000L;
        /** 假设的最低上传速度（字节/秒）。 */
        private long assumedSpeedBps = 102_400L;
        /** 单媒体允许的最大上传等待时间（毫秒）。 */
        private long maxTimeoutMs = 3_600_000L;
    }

    @Data
    public static class ProcessingRuntime {
        /** 处理任务超过该时长未更新时允许 GC 重新排队（毫秒）。 */
        private long processingTimeoutMs = 3_600_000L;
        /** 源文件、产物和 manifest HTTP 请求超时时间（毫秒）。 */
        private long httpRequestTimeoutMs = 120_000L;
        /** FFmpeg/FFprobe 单次命令超时时间（毫秒）。 */
        private long ffmpegTimeoutMs = 300_000L;
        /** 源 HLS 切片时长（秒）。 */
        private int hlsSegmentSeconds = 4;
    }

    @Data
    public static class Playback {
        /** 分片 URL 在视频时长之外额外保留的有效时间（秒）。 */
        private long hlsSegmentUrlTtlSeconds = 900L;
        /** 缓存清单的签名有效期安全余量（秒）。 */
        private long manifestCacheSafetySeconds = 30L;
        /** HLS manifest 构建锁有效期（秒）。 */
        private long manifestBuildLockTtlSeconds = 10L;
    }

    @Data
    public static class StaleCheck {
        /** GC 每类状态每次最多扫描的媒体数量。 */
        private int batchSize = 500;
    }
}
