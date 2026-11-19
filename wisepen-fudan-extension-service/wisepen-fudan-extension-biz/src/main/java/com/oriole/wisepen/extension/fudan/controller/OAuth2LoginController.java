package com.oriole.wisepen.extension.fudan.controller;

import com.oriole.wisepen.campus.extension.api.domain.dto.OAuth2ProviderDescriptorDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.VerifiedIdentityClaimsDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.OAuth2AuthorizeRequest;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.OAuth2ExchangeCodeRequest;
import com.oriole.wisepen.campus.extension.api.feign.RemoteOAuth2Service;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.extension.fudan.service.FudanOAuth2LoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "内部 - OAuth2.0 登录", description = "复旦统一身份认证 OAuth2.0 登录接口")
@RestController
@RequestMapping("/internal/oauth2Login")
@RequiredArgsConstructor
public class OAuth2LoginController implements RemoteOAuth2Service {

    private final FudanOAuth2LoginService fudanOAuth2LoginService;

    @Override
    @Operation(
            summary = "获取复旦 OAuth2.0 登录 Provider 描述",
            description = """
                    - 用途：让用户服务了解复旦 OAuth2.0 登录 Provider 的编码、展示名称和学校信息。
                    - 请求：无需请求参数。
                    - 约束：调用方必须通过内部服务调用边界访问本接口。
                    - 处理：返回复旦扩展服务声明的 OAuth2.0 登录 Provider 能力；不发起任何授权或令牌请求。
                    - 失败：发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回 Provider 编码、展示名称和学校名称。
                    """
    )
    @GetMapping("/getProviderDescriptor")
    public R<OAuth2ProviderDescriptorDTO> getProviderDescriptor() {
        return R.ok(fudanOAuth2LoginService.getProviderDescriptor());
    }

    @Override
    @Operation(
            summary = "构建复旦 OAuth2.0 授权页地址",
            description = """
                    - 用途：把用户服务生成的回调地址和 state 组装成复旦统一身份认证授权页地址。
                    - 请求：providerCode 必须为 FUDAN；redirectUri 为回调地址；state 为一次性状态值。
                    - 约束：调用方必须通过内部服务调用边界访问本接口；client_id 与 client_secret 必须已配置。
                    - 处理：按授权码模式拼接 response_type=code、client_id、redirect_uri 和 state 参数；不发起任何网络请求。
                    - 失败：Provider 编码不匹配或凭据未配置 -> FudanExtensionError.OAUTH_REQUEST_INVALID。
                    - 响应：返回学校授权页完整地址。
                    """
    )
    @PostMapping("/buildAuthorizationUrl")
    public R<String> buildAuthorizationUrl(@Valid @RequestBody OAuth2AuthorizeRequest request) {
        return R.ok(fudanOAuth2LoginService.buildAuthorizationUrl(request));
    }

    @Override
    @Operation(
            summary = "用授权码换取用户信息",
            description = """
                    - 用途：用学校回调返回的授权码换取 access_token，并拉取当前登录用户信息。
                    - 请求：providerCode 必须为 FUDAN；code 为授权码；redirectUri 与发起授权时一致。
                    - 约束：调用方必须通过内部服务调用边界访问本接口；授权码一次性且 30 分钟内有效。
                    - 处理：使用 client_id/client_secret Basic 认证调用令牌端点，再用 access_token 调用用户信息端点，归一化为标准身份 claims；不在日志或缓存中保存 token。
                    - 失败：令牌换取失败 -> FudanExtensionError.OAUTH_TOKEN_EXCHANGE_FAILED；用户信息获取失败 -> FudanExtensionError.OAUTH_USER_INFO_FAILED；缺少学工号或姓名 -> FudanExtensionError.OAUTH_USER_INFO_INCOMPLETE。
                    - 响应：返回归一化后的标准身份 claims。
                    """
    )
    @PostMapping("/exchangeCode")
    public R<VerifiedIdentityClaimsDTO> exchangeCode(@Valid @RequestBody OAuth2ExchangeCodeRequest request) {
        return R.ok(fudanOAuth2LoginService.exchangeCode(request));
    }
}
