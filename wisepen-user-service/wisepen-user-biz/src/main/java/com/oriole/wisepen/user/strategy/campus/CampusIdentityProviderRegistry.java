package com.oriole.wisepen.user.strategy.campus;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.campus.extension.api.feign.RemoteIdentityService;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.exception.UserError;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 将 Provider 编码解析为对应服务的标准 Feign 客户端。 */
@Component
public class CampusIdentityProviderRegistry {

    private final CampusIdentityProviderProperties properties;
    private final FeignClientBuilder feignClientBuilder;
    private final Map<String, RemoteIdentityService> clients = new ConcurrentHashMap<>();

    public CampusIdentityProviderRegistry(
            CampusIdentityProviderProperties properties,
            ApplicationContext applicationContext) {
        this.properties = properties;
        this.feignClientBuilder = new FeignClientBuilder(applicationContext);
    }

    public List<String> getEnabledProviderCodes() {
        return properties.getProviders().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .toList();
    }

    public RemoteIdentityService require(String providerCode) {
        CampusIdentityProviderProperties.ProviderConfig config = properties.getProviders().get(providerCode);
        if (config == null || !config.isEnabled() || StrUtil.isBlank(config.getServiceName())) {
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_PROVIDER_NOT_FOUND);
        }

        String serviceName = config.getServiceName().trim();
        return clients.computeIfAbsent(serviceName, this::createClient);
    }

    private RemoteIdentityService createClient(String serviceName) {
        return feignClientBuilder
                .forType(RemoteIdentityService.class, serviceName)
                .contextId("campus-identity-" + serviceName)
                .build();
    }
}
