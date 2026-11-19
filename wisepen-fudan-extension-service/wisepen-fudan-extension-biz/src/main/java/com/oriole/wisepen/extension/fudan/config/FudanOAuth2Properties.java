package com.oriole.wisepen.extension.fudan.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 复旦统一身份认证 OAuth2.0 客户端配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.fudan.oauth2")
public class FudanOAuth2Properties {

    /** 学校分配的 client_id */
    private String clientId;

    /** client_id 对应的 client_secret */
    private String clientSecret;

    /** 获取授权码端点 */
    private String authorizeUrl = "https://id.fudan.edu.cn/idp/authCenter/authenticate";

    /** 获取 Access Token 端点 */
    private String tokenUrl = "https://id.fudan.edu.cn/idp/api/v3/oauth2/token";

    /** 获取用户信息端点 */
    private String userInfoUrl = "https://id.fudan.edu.cn/idp/api/v3/oauth2/userInfo";
}
