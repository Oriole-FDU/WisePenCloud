package com.oriole.wisepen.user.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 用户服务配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.user")
public class UserProperties {

    private Endpoint endpoint = new Endpoint();
    private AdminPassword adminPassword = new AdminPassword();
    private MessageDedup messageDedup = new MessageDedup();
    private AiDebt aiDebt = new AiDebt();

    @Data
    public static class Endpoint {
        /** 用户浏览器可访问的前端或网关根地址，用于邮件中的验证和重置链接 */
        private String apiDomain;
        /** 认证 Cookie Domain */
        private String authCookieDomain;
    }

    @Data
    public static class AdminPassword {
        /** 管理员重置用户密码时使用的默认密码 */
        private String defaultPassword;
    }

    @Data
    public static class MessageDedup {
        /** 相同消息的重复判定窗口（分钟） */
        private long windowMinutes = 60;
    }

    @Data
    public static class AiDebt {
        /** 个人钱包允许的 AI Token 欠费硬上限 */
        private int hardLimitTokens = 10_000;
    }
}
