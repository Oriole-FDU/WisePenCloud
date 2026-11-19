package com.oriole.wisepen.campus.extension.api.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 请求扩展服务用授权码换取令牌并获取用户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ExchangeCodeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Provider 编码 */
    @NotBlank
    private String providerCode;

    /** 授权服务器回调携带的授权码 */
    @NotBlank
    private String code;

    /** 与发起授权时一致的 OAuth2.0 回调地址 */
    @NotBlank
    private String redirectUri;
}
