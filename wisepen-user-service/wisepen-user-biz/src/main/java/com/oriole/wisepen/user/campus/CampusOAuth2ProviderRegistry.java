package com.oriole.wisepen.user.campus;

import com.oriole.wisepen.campus.extension.api.feign.RemoteOAuth2Service;
import com.oriole.wisepen.user.exception.UserError;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** 将 Provider 编码解析为 OAuth2.0 协议扩展服务的标准 Feign 客户端 */
@Component
public class CampusOAuth2ProviderRegistry extends CampusProviderClientRegistry<RemoteOAuth2Service> {

    public CampusOAuth2ProviderRegistry(CampusProviderProperties properties, ApplicationContext applicationContext) {
        super(properties, applicationContext);
    }

    @Override
    protected Class<RemoteOAuth2Service> clientType() {
        return RemoteOAuth2Service.class;
    }

    @Override
    protected String contextIdPrefix() {
        return "oauth2-";
    }

    @Override
    protected UserError notFoundError() {
        return UserError.OAUTH_LOGIN_PROVIDER_NOT_FOUND;
    }
}
