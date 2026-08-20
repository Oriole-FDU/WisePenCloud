package com.oriole.wisepen.user.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 存储服务全局配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.user")
public class UserProperties {
    /** 用户浏览器可访问的前端或网关根地址，用于邮件中的验证和重置链接 */
    private String apiDomain;

    private String authCookieDomain;

    private String defaultPassword;
}
