package com.oriole.wisepen.common.config;

import com.oriole.wisepen.common.core.constant.CommonConstants;
import com.oriole.wisepen.common.core.context.GrayContextHolder;
import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
// 所有引入 common 的服务，都会自动扫描 "com.oriole.wisepen" 下所有的 @FeignClient
@EnableFeignClients(basePackages = "com.oriole.wisepen")
public class FeignConfiguration {
    // 这里也可以放一些全局的 Feign 配置，比如日志级别、拦截器等

    @Bean
    public RequestInterceptor isolationRequestInterceptor() {
        return template -> {
            String developer = GrayContextHolder.getDeveloperTag();
            if (StringUtils.hasText(developer)) {
                // 往下游传递 X-Developer
                template.header(CommonConstants.GRAY_HEADER_DEV_KEY, developer);
            }
        };
    }
}