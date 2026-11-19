package com.oriole.wisepen.user.campus;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.exception.UserError;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 providerCode 解析 serviceName，并缓存对应协议的 Feign 客户端
 */
public abstract class CampusProviderClientRegistry<T> {

    protected final CampusProviderProperties properties;
    private final FeignClientBuilder feignClientBuilder;
    private final Map<String, T> clients = new ConcurrentHashMap<>();

    protected CampusProviderClientRegistry(CampusProviderProperties properties, ApplicationContext applicationContext) {
        this.properties = properties;
        this.feignClientBuilder = new FeignClientBuilder(applicationContext);
    }

    /** 该注册表负责的 Feign 客户端类型 */
    protected abstract Class<T> clientType();

    /** Feign 客户端 contextId 前缀，用于区分不同协议的客户端 */
    protected abstract String contextIdPrefix();

    /** Provider 未配置时抛出的业务错误 */
    protected abstract UserError notFoundError();

    public T require(String providerCode) {
        CampusProviderProperties.ProviderConfig config = properties.getProviders().get(providerCode);
        if (config == null || StrUtil.isBlank(config.getServiceName())) {
            throw new ServiceException(notFoundError());
        }
        String serviceName = config.getServiceName().trim();
        return clients.computeIfAbsent(serviceName, this::createClient);
    }

    private T createClient(String serviceName) {
        return feignClientBuilder
                .forType(clientType(), serviceName)
                .contextId(contextIdPrefix() + serviceName)
                .build();
    }
}
