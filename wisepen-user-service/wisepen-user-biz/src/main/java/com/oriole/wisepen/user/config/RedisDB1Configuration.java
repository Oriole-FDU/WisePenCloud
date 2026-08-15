package com.oriole.wisepen.user.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

@Configuration
public class RedisDB1Configuration {

    @Bean("redis1Factory")
    public RedisConnectionFactory redis1Factory(RedisProperties redisProperties) {
        RedisStandaloneConfiguration config;

        if (StringUtils.hasText(redisProperties.getUrl())) {
            URI uri = URI.create(redisProperties.getUrl());
            config = new RedisStandaloneConfiguration(
                    uri.getHost(),
                    uri.getPort() > 0 ? uri.getPort() : 6379
            );

            String userInfo = uri.getUserInfo();
            if (StringUtils.hasText(userInfo)) {
                String[] parts = userInfo.split(":", 2);
                if (parts.length == 2 && StringUtils.hasText(parts[1])) {
                    config.setPassword(RedisPassword.of(parts[1]));
                }
            }
        } else {
            config = new RedisStandaloneConfiguration(
                    redisProperties.getHost(),
                    redisProperties.getPort()
            );
            if (StringUtils.hasText(redisProperties.getUsername())) {
                config.setUsername(redisProperties.getUsername());
            }
            if (StringUtils.hasText(redisProperties.getPassword())) {
                config.setPassword(RedisPassword.of(redisProperties.getPassword()));
            }
        }

        config.setDatabase(1);
        return new LettuceConnectionFactory(config);
    }

    @Bean("stringRedisTemplateDB1")
    public StringRedisTemplate stringRedisTemplateDB1(
            @Qualifier("redis1Factory") RedisConnectionFactory redis1Factory) {
        return new StringRedisTemplate(redis1Factory);
    }
}