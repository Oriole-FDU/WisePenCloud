package com.oriole.wisepen.extension.fudan.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.campus.extension.api.domain.dto.OAuth2ProviderDescriptorDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.VerifiedIdentityClaimsDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.OAuth2AuthorizeRequest;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.OAuth2ExchangeCodeRequest;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.extension.fudan.config.FudanOAuth2Properties;
import com.oriole.wisepen.extension.fudan.constant.FudanExtensionConstants;
import com.oriole.wisepen.extension.fudan.domain.dto.FudanOAuth2TokenResponse;
import com.oriole.wisepen.extension.fudan.domain.dto.FudanOAuth2UserInfoResponse;
import com.oriole.wisepen.extension.fudan.exception.FudanExtensionError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 复旦统一身份认证 OAuth2.0 授权码模式适配
 * 拼授权 URL、换 token、拉用户信息并归一化为标准 claims
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FudanOAuth2LoginService {

    private static final int HTTP_TIMEOUT_MS = 10_000;

    private final FudanOAuth2Properties properties;
    private final ObjectMapper objectMapper;

    public OAuth2ProviderDescriptorDTO getProviderDescriptor() {
        return OAuth2ProviderDescriptorDTO.builder()
                .providerCode(FudanExtensionConstants.PROVIDER_CODE)
                .displayName("复旦统一身份认证登录")
                .universityName(FudanExtensionConstants.UNIVERSITY_NAME)
                .build();
    }

    public String buildAuthorizationUrl(OAuth2AuthorizeRequest request) {
        validateProviderCode(request.getProviderCode());
        requireClientCredentials();

        return properties.getAuthorizeUrl()
                + "?response_type=code"
                + "&client_id=" + URLEncoder.encode(properties.getClientId(), StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(request.getRedirectUri(), StandardCharsets.UTF_8)
                + "&state=" + URLEncoder.encode(request.getState(), StandardCharsets.UTF_8);
    }

    public VerifiedIdentityClaimsDTO exchangeCode(OAuth2ExchangeCodeRequest request) {
        validateProviderCode(request.getProviderCode());
        requireClientCredentials();

        FudanOAuth2TokenResponse token = exchangeToken(request.getCode(), request.getRedirectUri());
        FudanOAuth2UserInfoResponse userInfo = fetchUserInfo(token.getAccess_token());
        return toClaims(userInfo);
    }

    private FudanOAuth2TokenResponse exchangeToken(String code, String redirectUri) {
        String authorization = "Basic " + Base64.getEncoder().encodeToString(
                (properties.getClientId() + ":" + properties.getClientSecret()).getBytes(StandardCharsets.UTF_8));

        String body;
        try {
            body = HttpRequest.post(properties.getTokenUrl())
                    .timeout(HTTP_TIMEOUT_MS)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", authorization)
                    .form("grant_type", "authorization_code")
                    .form("code", code)
                    .form("redirect_uri", redirectUri)
                    .execute()
                    .body();
        } catch (Exception e) {
            ServiceException failure = new ServiceException(FudanExtensionError.OAUTH_TOKEN_EXCHANGE_FAILED);
            failure.initCause(e);
            throw failure;
        }

        FudanOAuth2TokenResponse token;
        try {
            token = objectMapper.readValue(body, FudanOAuth2TokenResponse.class);
        } catch (JsonProcessingException e) {
            ServiceException failure = new ServiceException(FudanExtensionError.OAUTH_TOKEN_EXCHANGE_FAILED);
            failure.initCause(e);
            throw failure;
        }
        if (token == null || StrUtil.isBlank(token.getAccess_token())) {
            if (token != null && StrUtil.isNotBlank(token.getMsg())) {
                throw new ServiceException(FudanExtensionError.OAUTH_TOKEN_EXCHANGE_FAILED, token.getMsg());
            }
            throw new ServiceException(FudanExtensionError.OAUTH_TOKEN_EXCHANGE_FAILED);
        }
        return token;
    }

    private FudanOAuth2UserInfoResponse fetchUserInfo(String accessToken) {
        String body;
        try {
            body = HttpRequest.get(properties.getUserInfoUrl())
                    .timeout(HTTP_TIMEOUT_MS)
                    .header("Authorization", "Bearer " + accessToken)
                    .execute()
                    .body();
        } catch (Exception e) {
            ServiceException failure = new ServiceException(FudanExtensionError.OAUTH_USER_INFO_FAILED);
            failure.initCause(e);
            throw failure;
        }

        FudanOAuth2UserInfoResponse userInfo;
        try {
            userInfo = objectMapper.readValue(body, FudanOAuth2UserInfoResponse.class);
        } catch (JsonProcessingException e) {
            ServiceException failure = new ServiceException(FudanExtensionError.OAUTH_USER_INFO_FAILED);
            failure.initCause(e);
            throw failure;
        }
        if (userInfo == null || StrUtil.isNotBlank(userInfo.getErrcode())) {
            if (userInfo != null && StrUtil.isNotBlank(userInfo.getMsg())) {
                throw new ServiceException(FudanExtensionError.OAUTH_USER_INFO_FAILED, userInfo.getMsg());
            }
            throw new ServiceException(FudanExtensionError.OAUTH_USER_INFO_FAILED);
        }
        if (StrUtil.isBlank(userInfo.getUser_id()) || StrUtil.isBlank(userInfo.getUser_name())) {
            throw new ServiceException(FudanExtensionError.OAUTH_USER_INFO_INCOMPLETE);
        }
        return userInfo;
    }

    private VerifiedIdentityClaimsDTO toClaims(FudanOAuth2UserInfoResponse userInfo) {
        return VerifiedIdentityClaimsDTO.builder()
                .realName(userInfo.getUser_name())
                .campusNo(userInfo.getUser_id())
                .identityType(mapUserType(userInfo.getUser_type()))
                .universityName(FudanExtensionConstants.UNIVERSITY_NAME)
                .college(userInfo.getDepartment())
                .email(userInfo.getEmail())
                .mobile(userInfo.getMobile())
                .build();
    }

    private IdentityType mapUserType(String userType) {
        if (userType == null) {
            return IdentityType.STUDENT;
        }
        return switch (userType) {
            case "本专科生", "硕士生", "博士生", "留学生", "夜大" -> IdentityType.STUDENT;
            case "教职工", "导师", "校外导师", "外国专家", "挂职干部", "教务临时人员",
                 "进修教师", "附属医院", "OA用户", "临时账户", "博士后" -> IdentityType.TEACHER;
            default -> {
                log.warn("oauth user type unmapped. userType={}", userType);
                yield IdentityType.STUDENT;
            }
        };
    }

    private void validateProviderCode(String providerCode) {
        if (!FudanExtensionConstants.PROVIDER_CODE.equals(providerCode)) {
            throw new ServiceException(FudanExtensionError.OAUTH_REQUEST_INVALID, "Provider 编码不匹配");
        }
    }

    private void requireClientCredentials() {
        if (StrUtil.isBlank(properties.getClientId()) || StrUtil.isBlank(properties.getClientSecret())) {
            throw new ServiceException(FudanExtensionError.OAUTH_REQUEST_INVALID, "未配置 OAuth2.0 client_id/client_secret");
        }
    }
}
