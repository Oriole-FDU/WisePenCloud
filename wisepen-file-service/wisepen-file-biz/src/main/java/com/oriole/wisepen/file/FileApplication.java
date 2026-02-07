package com.oriole.wisepen.file;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 文件服务启动类
 *
 * @author Xiong.Heng
 */
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("com.oriole.wisepen.file.mapper")
public class FileApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileApplication.class, args);
    }
}
