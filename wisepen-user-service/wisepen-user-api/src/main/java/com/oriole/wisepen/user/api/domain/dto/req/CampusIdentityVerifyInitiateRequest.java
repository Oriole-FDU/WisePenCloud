package com.oriole.wisepen.user.api.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 用户服务发起校园身份 Provider 认证的请求
 */
@Data
public class CampusIdentityVerifyInitiateRequest {

    /** Provider 稳定编码 */
    @NotBlank
    private String providerCode;

    /** Provider 所需输入，例如 account/password */
    private Map<String, Object> payload;
}
