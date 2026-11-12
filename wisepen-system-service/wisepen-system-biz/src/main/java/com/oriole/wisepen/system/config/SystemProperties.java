package com.oriole.wisepen.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 系统服务配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.system")
public class SystemProperties {

    private Mail mail = new Mail();

    @Data
    public static class Mail {
        /** 发件人显示名称 */
        private String fromName = "WisePen系统";
    }
}
