package com.oriole.wisepen.common.gray;

import com.oriole.wisepen.common.core.constant.CommonConstants;
import com.oriole.wisepen.common.core.context.GrayContextHolder;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

public class GrayServiceInstanceListSupplier implements ServiceInstanceListSupplier {

    private final ServiceInstanceListSupplier delegate;

    public GrayServiceInstanceListSupplier(ServiceInstanceListSupplier delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getServiceId() {
        return delegate.getServiceId();
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        return delegate.get();
    }

    @Override
    public Flux<List<ServiceInstance>> get(Request request) {
        return delegate.get(request).map(instances -> {

            // 获取当前请求上下文中的开发者标签
            String targetDeveloper = GrayContextHolder.getDeveloperTag();

            // 如果没有标签，返回所有不带 developer 元数据的实例（主干环境）
            if (!StringUtils.hasText(targetDeveloper)) {
                return getStableInstances(instances);
            }

            // 筛选匹配 developer 标签的实例
            List<ServiceInstance> targetInstances = instances.stream()
                    .filter(instance -> targetDeveloper.equals(instance.getMetadata().get(CommonConstants.GRAY_METADATA_DEV_KEY)))
                    .collect(Collectors.toList());

            // 有专属实例走专属，没有则走主干
            if (!targetInstances.isEmpty()) {
                return targetInstances;
            } else {
                return getStableInstances(instances);
            }
        });
    }

    // 获取稳定版实例（即没有被任何人认领的实例）
    private List<ServiceInstance> getStableInstances(List<ServiceInstance> instances) {
        return instances.stream()
                .filter(instance -> !instance.getMetadata().containsKey(CommonConstants.GRAY_METADATA_DEV_KEY))
                .collect(Collectors.toList());
    }
}