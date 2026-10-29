package com.oriole.wisepen.resource.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 资源服务配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.resource")
public class ResourceProperties {

    private Retention retention = new Retention();
    private ReadCount readCount = new ReadCount();
    private DashboardCache dashboardCache = new DashboardCache();

    @Data
    public static class Retention {
        /** 软删除回收站保留天数 */
        private Integer deletedRetentionDays = 30;
    }

    @Data
    public static class ReadCount {
        /** 阅读量去重窗口时长（分钟） */
        private long dedupTtlMinutes = 10;
    }

    @Data
    public static class DashboardCache {
        /** 小组看板指标缓存有效天数 */
        private long metricTtlDays = 3;
    }
}