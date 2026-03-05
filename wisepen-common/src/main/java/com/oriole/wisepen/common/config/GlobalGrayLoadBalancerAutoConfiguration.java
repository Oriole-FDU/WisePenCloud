package com.oriole.wisepen.common.config;

import com.oriole.wisepen.common.gray.GrayLoadBalancerConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.config.LoadBalancerAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * 全局启用自定义负载均衡策略
 */
@Configuration(proxyBeanMethods = false)
@LoadBalancerClients(defaultConfiguration = GrayLoadBalancerConfiguration.class)
@AutoConfigureBefore(LoadBalancerAutoConfiguration.class)
public class GlobalGrayLoadBalancerAutoConfiguration {
}