package com.oriole.wisepen.campus.extension.api.feign;

import com.oriole.wisepen.campus.extension.api.domain.dto.OAuth2ProviderDescriptorDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.VerifiedIdentityClaimsDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.OAuth2AuthorizeRequest;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.OAuth2ExchangeCodeRequest;
import com.oriole.wisepen.common.core.domain.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * OAuth2.0 Provider 服务必须实现的标准 HTTP 协议
 */
public interface RemoteOAuth2Service {

    @GetMapping("/internal/oauth2Login/getProviderDescriptor")
    R<OAuth2ProviderDescriptorDTO> getProviderDescriptor();

    @PostMapping("/internal/oauth2Login/buildAuthorizationUrl")
    R<String> buildAuthorizationUrl(@Valid @RequestBody OAuth2AuthorizeRequest request);

    @PostMapping("/internal/oauth2Login/exchangeCode")
    R<VerifiedIdentityClaimsDTO> exchangeCode(@Valid @RequestBody OAuth2ExchangeCodeRequest request);
}
