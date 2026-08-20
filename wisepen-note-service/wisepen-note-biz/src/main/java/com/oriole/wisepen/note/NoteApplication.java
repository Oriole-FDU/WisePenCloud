package com.oriole.wisepen.note;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableDiscoveryClient
@EnableScheduling
@EnableMongoAuditing
@SpringBootApplication
public class NoteApplication {
    public static void main(String[] args) {
        SpringApplication.run(NoteApplication.class, args);
    }
}
