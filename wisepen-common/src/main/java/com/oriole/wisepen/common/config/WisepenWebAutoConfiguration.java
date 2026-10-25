package com.oriole.wisepen.common.config;

import com.oriole.wisepen.common.web.interceptor.FeignRequestInterceptor;
import com.oriole.wisepen.common.web.interceptor.HeaderInterceptor;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

@Configuration
// 显式扫描 common 包，把 SaPermissionImpl, GlobalExceptionHandler 等扫进去
@ComponentScan(basePackages = "com.oriole.wisepen.common")
public class WisepenWebAutoConfiguration implements WebMvcConfigurer {

    // 读取配置中的安全密钥
    // 默认值为 APISIX-wX0iR6tY，可在 Nacos 中覆盖
    @Value("${wisepen.security.from-source:APISIX-wX0iR6tY}")
    private String fromSource;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册头部拦截器，拦截所有路径
        registry.addInterceptor(new HeaderInterceptor(fromSource))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login", "/auth/register",
                        "/external/**",
                        "/docs", "/docs/**",
                        "/v3/api-docs/**"     // 放行 OpenAPI 的 JSON 接口
               );
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        // query 参数与 JSON body 共用同一套 canonical wire value；旧数字 code 仅作迁移期兼容。
        registry.addConverter(String.class, IdentityType.class, IdentityType::fromValue);
        registry.addConverter(String.class, GroupType.class, GroupType::fromValue);
        registry.addConverter(String.class, GroupRoleType.class, GroupRoleType::fromValue);
    }
}
