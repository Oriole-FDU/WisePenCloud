package com.oriole.wisepen.file.storage.strategy;

import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.config.StorageProperties;
import com.oriole.wisepen.file.storage.exception.FileStorageError;
import com.oriole.wisepen.file.storage.strategy.aliyun.AliyunOssProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储多实例路由器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageManager {

    private final StorageProperties storageProperties;

    // 存储实例缓存池
    private final Map<String, StorageProvider> providerCache = new ConcurrentHashMap<>();

    // 首要存储提供商编码
    private String primaryProviderCode;

    /**
     * 系统启动时自动加载并初始化所有启用的存储实例
     */
    @PostConstruct
    public synchronized void init() {
        log.info("storage provider load started. task=storageManagerInit");
        // 销毁旧实例
        destroyAll();

        StorageProperties.Providers providers = storageProperties.getProviders();
        if (providers.getPrimaryCode() == null || providers.getPrimaryCode().isBlank()) {
            throw new IllegalStateException("wisepen.storage.providers.primary-code must not be blank");
        }

        for (Map.Entry<String, StorageProperties.Provider> entry : providers.getItems().entrySet()) {
            String providerCode = entry.getKey();
            StorageProperties.Provider config = entry.getValue();
            if (providerCode == null || providerCode.isBlank()) {
                throw new IllegalStateException("storage provider code must not be blank");
            }
            if (config == null) {
                throw new IllegalStateException("storage provider config must not be null: " + providerCode);
            }
            if (!Boolean.TRUE.equals(config.getEnabled())) {
                continue;
            }
            providerCache.put(providerCode, buildProvider(providerCode, config));
        }

        this.primaryProviderCode = providers.getPrimaryCode();
        if (!providerCache.containsKey(primaryProviderCode)) {
            throw new IllegalStateException("primary storage provider must exist and be enabled: " + primaryProviderCode);
        }
        log.info("storage provider load finished. task=storageManagerInit providerCount={} primaryProviderCode={}",
                providerCache.size(), primaryProviderCode);
    }

    /**
     * 根据具体提供商类型，通过工厂方法构建实例
     */
    private StorageProvider buildProvider(String providerCode, StorageProperties.Provider config) {
        return switch (config.getProvider()) {
            case ALIYUN_OSS -> new AliyunOssProvider(providerCode, config);
            // 预留其他云厂商的扩展口
            // case MINIO -> new MinioProvider(config);
            default -> throw new ServiceException(FileStorageError.CANNOT_SUPPORT_STORAGE_PROVIDER);
        };
    }

    /**
     * 获取首要存储实例
     */
    public StorageProvider getPrimaryProvider() {
        StorageProvider provider = providerCache.get(primaryProviderCode);
        if (provider == null) {
            throw new ServiceException(FileStorageError.CANNOT_SUPPORT_STORAGE_PROVIDER);
        }
        return provider;
    }

    /**
     * 根据提供商编码获取指定存储实例
     */
    public StorageProvider getProvider(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return getPrimaryProvider();
        }
        StorageProvider provider = providerCache.get(providerCode);
        if (provider == null) {
            throw new ServiceException(FileStorageError.CANNOT_SUPPORT_STORAGE_PROVIDER);
        }
        return provider;
    }

    /**
     * 根据 Domain 反向路由获取存储实例 (用于根据 URL 删除文件)
     */
    public StorageProvider getProviderByDomain(String domain) {
        for (StorageProvider provider : providerCache.values()) {
            // 简单去斜杠匹配
            if (provider.getDomain().replaceAll("/$", "").equals(domain.replaceAll("/$", ""))) {
                return provider;
            }
        }
        throw new ServiceException(FileStorageError.FILE_URL_INVALID);
    }

    /**
     * 获取所有启用的存储实例 (用于兜底遍历查询)
     */
    public List<StorageProvider> getAllProviders() {
        return new ArrayList<>(providerCache.values());
    }

    @PreDestroy
    public void destroyAll() {
        int providerCount = providerCache.size();
        providerCache.values().forEach(StorageProvider::destroy);
        providerCache.clear();
        primaryProviderCode = null;
        log.info("storage provider destroyed. providerCount={}", providerCount);
    }
}
