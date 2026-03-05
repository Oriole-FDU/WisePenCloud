package com.oriole.wisepen.file;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 文件服务启动类
 *
 * @author Xiong.Heng
 */
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("com.oriole.wisepen.file.mapper")
@EnableFeignClients(basePackages = "com.oriole.wisepen.resource.feign")
public class FileApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileApplication.class, args);
    }
}
