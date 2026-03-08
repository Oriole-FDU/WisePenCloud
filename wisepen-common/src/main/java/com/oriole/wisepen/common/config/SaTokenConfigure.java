package com.oriole.wisepen.common.config;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.jwt.StpLogicJwtForMixin;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static com.oriole.wisepen.common.core.constant.SecurityConstants.COOKIE_AUTHORIZATION_TOKEN;

/**
 * Sa-Token 全局策略配置
 * 作用：强制所有微服务都使用 JWT 混入模式 (JWT + Redis)
 */
@Configuration
public class SaTokenConfigure {

    @Bean
    public StpLogic getStpLogicJwt() {
        // 开启 Mixin 模式
        // 生成的 Token 是 JWT 格式 (APISIX 可读)
        // 后端依然在 Redis 存 Session (可管理、可强退)
        return new StpLogicJwtForMixin();
    }

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "sa-token")
    public SaTokenConfig getSaTokenConfigPrimary() {
        SaTokenConfig config = new SaTokenConfig();

        config.setTokenName(COOKIE_AUTHORIZATION_TOKEN); // token 名称（同时也是 cookie 名称）
        config.setActiveTimeout(-1); // token 最低活跃频率（单位：秒），如果 token 超过此时间没有访问系统就会被冻结，默认-1 代表不限制，永不冻结
        config.setIsConcurrent(true); // 是否允许同一账号多地同时登录（为 true 时允许一起登录，为 false 时新登录挤掉旧登录）
        config.setIsShare(false); // 在多人登录同一账号时，是否共用一个 token （为 true 时所有登录共用一个 token，为 false 时每次登录新建一个 token）
        config.setIsLog(false); // 是否输出操作日志

        config.cookie.setHttpOnly(true);
        return config;
    }

}