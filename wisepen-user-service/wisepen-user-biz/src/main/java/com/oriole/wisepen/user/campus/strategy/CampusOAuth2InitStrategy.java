package com.oriole.wisepen.user.campus.strategy;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.OAuth2AuthorizeRequest;
import com.oriole.wisepen.campus.extension.api.feign.RemoteOAuth2Service;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.config.UserProperties;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.campus.CampusAuthProtocol;
import com.oriole.wisepen.user.campus.CampusProviderProperties;
import com.oriole.wisepen.user.campus.CampusOAuth2ProviderRegistry;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.strategy.login.UserLoginInitStrategy;
import com.oriole.wisepen.user.strategy.verification.UserVerificationInitStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class CampusOAuth2InitStrategy implements
        UserVerificationInitStrategy<UserVerificationInitStrategy.CampusProviderOAuthInitCommand, String>,
        UserLoginInitStrategy<UserLoginInitStrategy.CampusProviderOAuthInitCommand, String>
{

    private final CampusProviderProperties properties;
    private final CampusOAuth2ProviderRegistry providerRegistry;
    private final RedisCacheManager redisCacheManager;
    private final UserProperties userProperties;

    public String initLogin(UserLoginInitStrategy.CampusProviderOAuthInitCommand command) {
        return buildLoginAuthorizationUrl(command.providerCode());
    }

    public String initVerification(UserVerificationInitStrategy.CampusProviderOAuthInitCommand command) {
        return buildVerifyAuthorizationUrl(command.userId(), command.providerCode());
    }

    /** 发起 OAuth2 登录授权，返回学校授权页地址。 */
    private String buildLoginAuthorizationUrl(String providerCode) {
        properties.requireLoginProtocol(providerCode, CampusAuthProtocol.OAUTH2, UserError.OAUTH_LOGIN_PROVIDER_NOT_FOUND);

        String callbackBaseUrl = StrUtil.blankToDefault(userProperties.getEndpoint().getApiDomain(), "").trim();
        if (callbackBaseUrl.isEmpty()) throw new ServiceException(UserError.OAUTH_LOGIN_FAILED, "OAuth2.0 回调地址未配置");

        String state = IdUtil.fastSimpleUUID();
        redisCacheManager.setOAuth2LoginState(state, providerCode);
        RemoteOAuth2Service provider = providerRegistry.require(providerCode);
        return requireRemoteData(
                provider.buildAuthorizationUrl(OAuth2AuthorizeRequest.builder()
                        .providerCode(providerCode)
                        .redirectUri(buildLoginRedirectUri(callbackBaseUrl))
                        .state(state)
                        .build()),
                UserError.OAUTH_LOGIN_REQUEST_FAILED
        );
    }

    /** 发起 OAuth2 身份认证授权，返回学校授权页地址。 */
    private String buildVerifyAuthorizationUrl(Long userId, String providerCode) {
        properties.requireIdentityProtocol(providerCode, CampusAuthProtocol.OAUTH2, UserError.VERIFICATION_CAMPUS_IDENTITY_PROVIDER_NOT_FOUND);

        String callbackBaseUrl = StrUtil.blankToDefault(userProperties.getEndpoint().getApiDomain(), "").trim();
        if (callbackBaseUrl.isEmpty()) throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED, "OAuth2.0 回调地址未配置");

        String state = IdUtil.fastSimpleUUID();
        redisCacheManager.setOAuth2IdentityState(state, userId, providerCode);
        RemoteOAuth2Service provider = providerRegistry.require(providerCode);
        return requireRemoteData(
                provider.buildAuthorizationUrl(OAuth2AuthorizeRequest.builder()
                        .providerCode(providerCode)
                        .redirectUri(buildVerifyRedirectUri(callbackBaseUrl))
                        .state(state)
                        .build()),
                UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED
        );
    }

    private <T> T requireRemoteData(R<T> response, UserError error) {
        if (response == null || !Integer.valueOf(200).equals(response.getCode())) {
            if (response != null && StrUtil.isNotBlank(response.getMsg())) {
                throw new ServiceException(error, response.getMsg());
            }
            throw new ServiceException(error);
        }
        if (response.getData() == null) {
            throw new ServiceException(error);
        }
        return response.getData();
    }

    public static String buildLoginRedirectUri(String callbackBaseUrl) {
        return buildRedirectUri(callbackBaseUrl, "/auth/campusOAuthLoginCallback");
    }

    public static String buildVerifyRedirectUri(String callbackBaseUrl) {
        return buildRedirectUri(callbackBaseUrl, "/user/campusOAuthIdentityVerifyCallback");
    }

    private static String buildRedirectUri(String callbackBaseUrl, String path) {
        String base = callbackBaseUrl.endsWith("/")
                ? callbackBaseUrl.substring(0, callbackBaseUrl.length() - 1)
                : callbackBaseUrl;
        return base + path;
    }
}
