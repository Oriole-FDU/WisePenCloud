package com.oriole.wisepen.common.config;

import com.oriole.wisepen.common.web.interceptor.HeaderInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
// 显式扫描 common 包，把 SaPermissionImpl, GlobalExceptionHandler 等扫进去
@ComponentScan(basePackages = "com.oriole.wisepen.common")
public class WisepenWebAutoConfiguration implements WebMvcConfigurer {

    @Value("${wisepen.security.from-source:APISIX-wX0iR6tY}")
    private String fromSource;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册头部拦截器，拦截所有路径
        registry.addInterceptor(new HeaderInterceptor(fromSource))
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/auth/register");
    }
}