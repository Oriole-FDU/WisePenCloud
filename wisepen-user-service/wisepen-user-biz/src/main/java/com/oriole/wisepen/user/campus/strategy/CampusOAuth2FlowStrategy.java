package com.oriole.wisepen.user.campus.strategy;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.campus.extension.api.domain.dto.VerifiedIdentityClaimsDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.OAuth2ExchangeCodeRequest;
import com.oriole.wisepen.campus.extension.api.enums.CampusAuthProtocol;
import com.oriole.wisepen.campus.extension.api.feign.RemoteOAuth2Service;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.config.UserProperties;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.campus.CampusProviderProperties;
import com.oriole.wisepen.user.campus.CampusOAuth2ProviderRegistry;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.strategy.login.UserLoginCompleteStrategy;
import com.oriole.wisepen.user.strategy.login.UserLoginService;
import com.oriole.wisepen.user.strategy.verification.UserVerificationCompleteStrategy;
import com.oriole.wisepen.user.strategy.verification.UserVerificationService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OAuth2.0 协议的回调编排：校验 state、换取标准 claims，并把 claims 交给对应的完成策略。
 */
@Component
@RequiredArgsConstructor
public class CampusOAuth2FlowStrategy {

    private final CampusProviderProperties properties;
    private final CampusOAuth2ProviderRegistry providerRegistry;
    private final RedisCacheManager redisCacheManager;
    private final UserProperties userProperties;
    private final UserLoginService userLoginService;
    private final UserVerificationService userVerificationService;

    /** OAuth2.0 登录回调：校验 state、换取 claims 并建立会话。 */
    public String completeLogin(String code, String state) {
        String providerCode = redisCacheManager.getOAuth2LoginState(state);
        if (!StringUtils.hasText(providerCode)) throw new ServiceException(UserError.OAUTH_AUTH_STATE_INVALID);

        properties.requireLoginProtocol(providerCode, CampusAuthProtocol.OAUTH2, UserError.AUTH_CAMPUS_OAUTH_PROVIDER_NOT_FOUND);

        String callbackBaseUrl = StrUtil.blankToDefault(userProperties.getEndpoint().getApiDomain(), "").trim();
        if (callbackBaseUrl.isEmpty()) throw new ServiceException(UserError.OAUTH_AUTH_FAILED, "OAuth2.0 回调地址未配置");

        String redirectUri = CampusOAuth2InitStrategy.buildLoginRedirectUri(callbackBaseUrl);

        RemoteOAuth2Service provider = providerRegistry.require(providerCode);
        VerifiedIdentityClaimsDTO claims = requireRemoteData(
                provider.exchangeCode(OAuth2ExchangeCodeRequest.builder()
                        .providerCode(providerCode)
                        .code(code)
                        .redirectUri(redirectUri)
                        .build()),
                UserError.OAUTH_AUTH_FAILED
        );

        if (claims == null || StrUtil.isBlank(claims.getCampusNo())) {
            throw new ServiceException(UserError.OAUTH_AUTH_FAILED, "校园用户信息缺少学工号");
        }
        return userLoginService.completeLogin(new UserLoginCompleteStrategy.CampusProviderCompleteCommand(providerCode, claims));
    }

    /** OAuth2.0 身份认证回调：校验 state、换取 claims 并挂到当前用户。 */
    public void completeIdentity(String code, String state) {
        ImmutablePair<Long, String> bound = redisCacheManager.getOAuth2IdentityState(state);
        if (bound == null) {
            throw new ServiceException(UserError.OAUTH_AUTH_STATE_INVALID);
        }
        String providerCode = bound.getRight();
        Long userId = bound.getLeft();

        properties.requireIdentityProtocol(providerCode, CampusAuthProtocol.OAUTH2, UserError.AUTH_CAMPUS_OAUTH_PROVIDER_NOT_FOUND);

        String callbackBaseUrl = StrUtil.blankToDefault(userProperties.getEndpoint().getApiDomain(), "").trim();
        if (callbackBaseUrl.isEmpty()) throw new ServiceException(UserError.OAUTH_AUTH_FAILED, "OAuth2.0 回调地址未配置");

        String redirectUri = CampusOAuth2InitStrategy.buildVerifyRedirectUri(callbackBaseUrl);

        RemoteOAuth2Service provider = providerRegistry.require(providerCode);
        VerifiedIdentityClaimsDTO claims = requireRemoteData(
                provider.exchangeCode(OAuth2ExchangeCodeRequest.builder()
                        .providerCode(providerCode)
                        .code(code)
                        .redirectUri(redirectUri)
                        .build()),
                UserError.OAUTH_AUTH_FAILED
        );

        userVerificationService.completeVerification(
                new UserVerificationCompleteStrategy.CampusProviderCompleteCommand(userId, providerCode, claims)
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
}
