package com.oriole.wisepen.generic.resource.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 通用资源服务配置属性
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "wisepen.generic-resource")
public class GenericResourceProperties {

    @Valid
    private UploadTimeout uploadTimeout = new UploadTimeout();
    @Valid
    private StaleCheck staleCheck = new StaleCheck();

    @Data
    public static class UploadTimeout {
        /** 与文件大小无关的最低等待时间（毫秒），默认 10 分钟 */
        @Min(1)
        private long baseTimeoutMs = 600_000L;
        /** 假设的最低上传速度（字节/秒），默认 100 KB/s */
        @Min(1)
        private long assumedSpeedBps = 102_400L;
        /** 单资源允许的最大超时时长（毫秒），默认 60 分钟 */
        @Min(1)
        private long maxTimeoutMs = 3_600_000L;
    }

    @Data
    public static class StaleCheck {
        /** 上传检查和注册补偿每轮各处理的最大条数。 */
        @Min(1)
        @Max(1000)
        private int batchSize = 100;
        /** 注册任务超过此时间仍未完成时允许补偿，失败任务也按此间隔重试。 */
        @Min(1)
        private long registrationTimeoutMs = 300_000L;
    }
}
