package com.oriole.wisepen.user.campus;

import com.oriole.wisepen.campus.extension.api.feign.RemoteClassicAuthService;
import com.oriole.wisepen.user.exception.UserError;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 将 Provider 编码解析为经典认证协议扩展服务的标准 Feign 客户端 */
@Component
public class CampusClassicAuthProviderRegistry extends CampusProviderClientRegistry<RemoteClassicAuthService> {

    public CampusClassicAuthProviderRegistry(CampusProviderProperties properties, ApplicationContext applicationContext) {
        super(properties, applicationContext);
    }

    /** 列出已启用经典身份认证的 Provider 编码 */
    public List<String> getEnabledProviderCodes() {
        return properties.getProviders().entrySet().stream()
                .filter(entry -> entry.getValue() != null
                        && entry.getValue().getIdentity() != null
                        && entry.getValue().getIdentity().contains(CampusAuthProtocol.CLASSIC))
                .map(Map.Entry::getKey)
                .toList();
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
        return UserError.VERIFICATION_CAMPUS_IDENTITY_PROVIDER_NOT_FOUND;
    }
}
