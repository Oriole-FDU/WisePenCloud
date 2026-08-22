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

    /**
     * 播放/预览会话默认过期时间。
     */
    private long sessionTtlMinutes = 30L;

    /**
     * 明水印固定声明文本。
     */
    private String academicUseText = "ACADEMIC USE ONLY";

    /**
     * 本地媒体处理缓存目录。
     */
    private String cachePath = "/tmp/wisepen/media/cache/";

    /**
     * 定时任务检测 UPLOADING 媒体的执行间隔（毫秒），默认 5 分钟。
     */
    private long staleCheckDelayMs = 300_000L;

    /**
     * 上传超时计算：基础超时时长（毫秒），与文件大小无关的最低等待时间，默认 10 分钟。
     */
    private long baseTimeoutMs = 600_000L;

    /**
     * 上传超时计算：假设的最低上传速度（字节/秒），默认 100 KB/s。
     * timeout = max(baseTimeout, min(maxTimeout, size / assumedSpeedBps * 1000))
     */
    private long assumedSpeedBps = 102_400L;

    /**
     * 上传超时计算：单媒体允许的最大超时时长（毫秒），默认 60 分钟。
     */
    private long maxTimeoutMs = 3_600_000L;

    /**
     * FFmpeg 可执行文件路径。
     */
    private String ffmpegPath = "ffmpeg";

    /**
     * FFprobe 可执行文件路径。
     */
    private String ffprobePath = "ffprobe";

    /**
     * FFmpeg/FFprobe 单次命令超时时间。
     */
    private long ffmpegTimeoutMs = 300_000L;

    /**
     * 源 HLS 切片时长，单位秒。
     */
    private int hlsSegmentSeconds = 4;

    /**
     * HLS segment 防盗链 URL 有效期。
     */
    private long hlsSegmentUrlTtlSeconds = 900L;

    /**
     * HLS manifest 缓存时预留的安全余量，避免缓存内容中的 segment URL 临近过期。
     */
    private long playbackManifestCacheSafetySeconds = 30L;

    /**
     * HLS manifest 构建锁 TTL。
     */
    private long playbackManifestBuildLockTtlSeconds = 10L;
}
