package com.oriole.wisepen.file.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/**
 * 本地测试专用的 Feign 拦截器
 * 用于给下游微服务（如 resource-service）自动补充认证 Header，避免 404
 * 注意：此文件已被加入 .gitignore，不应被提交到远程仓库
 */
@Configuration
public class FeignAuthInterceptorConfig {

    @Bean
    public RequestInterceptor feignAuthInterceptor() {
        return template -> {
            // 补充网关防伪造 Header
            template.header("X-From-Source", "APISIX-wX0iR6tY");
            
            // 补充当前用户 ID，如果未获取到则默认传 1 保证测试能通
            String userId = SecurityContextHolder.getUserId();
            if (StringUtils.hasText(userId)) {
                template.header("X-User-Id", userId);
            } else {
                template.header("X-User-Id", "1");
            }
        };
    }
}
