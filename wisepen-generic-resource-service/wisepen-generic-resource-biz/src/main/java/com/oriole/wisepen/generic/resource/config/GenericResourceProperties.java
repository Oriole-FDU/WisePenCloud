package com.oriole.wisepen.generic.resource.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通用资源服务配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.generic-resource")
public class GenericResourceProperties {

    /**
     * 定时任务检测 UPLOADING 通用资源的执行间隔（毫秒），默认 5 分钟。
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
     * 上传超时计算：单资源允许的最大超时时长（毫秒），默认 60 分钟。
     */
    private long maxTimeoutMs = 3_600_000L;
}
