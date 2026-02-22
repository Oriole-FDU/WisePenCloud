package com.oriole.wisepen.resource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
public class ResPermissionApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResPermissionApplication.class, args);
    }
}