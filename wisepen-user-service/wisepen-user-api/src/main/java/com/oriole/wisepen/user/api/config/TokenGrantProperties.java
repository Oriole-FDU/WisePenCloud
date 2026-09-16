package com.oriole.wisepen.user.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Token 赠款配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.token-grant")
public class TokenGrantProperties {

    private Map<String, Rule> rules = new HashMap<>();

    @Data
    public static class Rule {
        private Boolean enabled = false;
        private Integer tokenAmount = 0;
    }
}
