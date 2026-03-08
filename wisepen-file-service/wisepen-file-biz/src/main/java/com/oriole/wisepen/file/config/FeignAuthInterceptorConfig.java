package com.oriole.wisepen.file.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
            
            // 尝试从当前请求中提取 X-User-Id
            String userId = "1";
            org.springframework.web.context.request.RequestAttributes requestAttributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                jakarta.servlet.http.HttpServletRequest request = ((org.springframework.web.context.request.ServletRequestAttributes) requestAttributes).getRequest();
                String headerUserId = request.getHeader("X-User-Id");
                if (StringUtils.hasText(headerUserId)) {
                    userId = headerUserId;
                }
            }
            template.header("X-User-Id", userId);
        };
    }
}
