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
