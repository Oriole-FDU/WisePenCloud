package com.oriole.wisepen.user.strategy.campus;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户服务侧的校园身份 Provider 注册表。
 *
 * <p>新增学校时只需要添加 Provider 配置和独立扩展服务，不需要在用户服务
 * 中添加学校专属字段或认证客户端。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.campus-identity")
public class CampusIdentityProviderProperties {

    /** Provider 编码到扩展服务配置的映射，Key 需要与前端传入的 providerCode 保持一致。 */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    @Data
    public static class ProviderConfig {
        /** 是否启用该 Provider；关闭后用户服务不会向该扩展服务发起认证请求。 */
        private boolean enabled = true;

        /** 注册中心中的扩展服务名；Provider 的展示信息由扩展服务 descriptor 返回。 */
        private String serviceName;
    }
}
