package com.oriole.wisepen.questionnaire;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@EnableDiscoveryClient
@EnableMongoAuditing
@SpringBootApplication
public class QuestionnaireApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuestionnaireApplication.class, args);
    }
}
