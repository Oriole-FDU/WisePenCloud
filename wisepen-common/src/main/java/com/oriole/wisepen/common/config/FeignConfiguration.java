package com.oriole.wisepen.common.config;

import com.oriole.wisepen.common.core.constant.CommonConstants;
import com.oriole.wisepen.common.core.constant.SecurityConstants;
import com.oriole.wisepen.common.core.context.GrayContextHolder;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableFeignClients(basePackages = "com.oriole.wisepen")
public class FeignConfiguration {

    // 1. 读取配置中的安全密钥 (默认值需与 WisepenWebAutoConfiguration 保持一致)
    @Value("${wisepen.security.from-source:APISIX-wX0iR6tY}")
    private String fromSource;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            // 2. 添加内部服务调用鉴权 Header
            template.header(SecurityConstants.HEADER_FROM_SOURCE, fromSource);

            // 3. 灰度发布逻辑
            String developer = GrayContextHolder.getDeveloperTag();
            if (StringUtils.hasText(developer)) {
                template.header(CommonConstants.GRAY_HEADER_DEV_KEY, developer);
            }
        };
    }
}