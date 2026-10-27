package com.oriole.wisepen.common.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oriole.wisepen.common.web.decoder.RemoteResponseErrorDecoder;
import com.oriole.wisepen.common.web.interceptor.FeignRequestInterceptor;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Configuration
@EnableFeignClients(basePackages = "com.oriole.wisepen")
public class FeignConfiguration {

    // 读取配置中的安全密钥
    // 默认值为 APISIX-wX0iR6tY，可在 Nacos 中覆盖
    @Value("${wisepen.security.from-source:APISIX-wX0iR6tY}")
    private String fromSource;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new FeignRequestInterceptor(fromSource);
    }

    private ObjectMapper feignObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
            @Override
            public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(parser.getLongValue()), ZoneId.systemDefault());
                }
                String value = parser.getValueAsString();
                if (value == null || value.isBlank()) return null;
                if (value.chars().allMatch(Character::isDigit)) {
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(value)), ZoneId.systemDefault());
                }
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        });
        objectMapper.registerModule(javaTimeModule);
        objectMapper.findAndRegisterModules();
        // 禁用将日期序列化为时间戳
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return objectMapper;
    }

    @Bean
    public Encoder feignEncoder() {
        HttpMessageConverters converters = new HttpMessageConverters(
                new MappingJackson2HttpMessageConverter(feignObjectMapper())
        );
        ObjectFactory<HttpMessageConverters> factory = () -> converters;
        return new SpringEncoder(factory);
    }

    @Bean
    public Decoder feignDecoder() {
        HttpMessageConverters converters = new HttpMessageConverters(
                new MappingJackson2HttpMessageConverter(feignObjectMapper())
        );
        ObjectFactory<HttpMessageConverters> factory = () -> converters;
        return new ResponseEntityDecoder(new SpringDecoder(factory));
    }

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new RemoteResponseErrorDecoder(feignObjectMapper());
    }
}
