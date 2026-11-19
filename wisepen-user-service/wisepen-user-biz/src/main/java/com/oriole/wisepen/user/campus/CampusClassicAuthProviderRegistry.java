package com.oriole.wisepen.user.campus;

import com.oriole.wisepen.campus.extension.api.feign.RemoteClassicAuthService;
import com.oriole.wisepen.user.exception.UserError;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** 将 Provider 编码解析为经典认证协议扩展服务的标准 Feign 客户端 */
@Component
public class CampusClassicAuthProviderRegistry extends CampusProviderClientRegistry<RemoteClassicAuthService> {

    public CampusClassicAuthProviderRegistry(CampusProviderProperties properties, ApplicationContext applicationContext) {
        super(properties, applicationContext);
    }

    @Override
    protected Class<RemoteClassicAuthService> clientType() {
        return RemoteClassicAuthService.class;
    }

    @Override
    protected String contextIdPrefix() {
        return "classic-auth-";
    }

    @Override
    protected UserError notFoundError() {
        return UserError.AUTH_CAMPUS_CLASSIC_PROVIDER_NOT_FOUND;
    }
}
