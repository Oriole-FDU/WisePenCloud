package com.oriole.wisepen.common.log.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync // 开启异步支持，否则 @Async 不生效
@ComponentScan(basePackages = "com.oriole.wisepen.common.log") // 扫描本包下的 Bean
public class LogAutoConfiguration {
}