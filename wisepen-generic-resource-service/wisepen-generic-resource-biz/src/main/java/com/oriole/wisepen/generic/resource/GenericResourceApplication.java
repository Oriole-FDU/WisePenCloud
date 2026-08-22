package com.oriole.wisepen.generic.resource;

import com.oriole.wisepen.common.config.FeignConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 通用资源服务启动类
 */
@EnableDiscoveryClient
@SpringBootApplication
@EnableMongoAuditing
@EnableScheduling
@Import(FeignConfiguration.class)
public class GenericResourceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GenericResourceApplication.class, args);
    }
}
