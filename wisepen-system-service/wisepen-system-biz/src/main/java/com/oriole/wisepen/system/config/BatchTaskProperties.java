package com.oriole.wisepen.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 批量任务执行配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.batch-task")
public class BatchTaskProperties {

    private Execution execution = new Execution();

    @Data
    public static class Execution {
        /** 单批执行任务项数量 */
        private int batchSize = 20;
        /** 执行中任务项超时分钟数 */
        private long staleMinutes = 30;
    }
}
