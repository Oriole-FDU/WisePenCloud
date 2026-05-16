package com.oriole.wisepen.resource.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.springframework.context.annotation.Bean;

/**
 * Elasticsearch 仓储扫描配置。
 * <p>
 * 仅显式扫描资源域仓储包，避免 Spring Data Elasticsearch 与 MongoDB Repository 互相抢扫描；
 * 底层 RestClient / 序列化配置交给 spring-boot-starter-data-elasticsearch 自动装配。
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.oriole.wisepen.resource.repository")
public class ESConfiguration {
    @Bean
    public JacksonJsonpMapper jacksonJsonpMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 因为es里没有java8时间模块而esindex实体的updatetime需要，故手动注册 Java 8 时间模块
        objectMapper.registerModule(new JavaTimeModule());
        return new JacksonJsonpMapper(objectMapper);
    }
}

