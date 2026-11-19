package com.oriole.wisepen.campus.extension.api.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 请求扩展服务构建 OAuth2.0 授权页地址
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2AuthorizeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Provider 编码 */
    @NotBlank
    private String providerCode;

    /** OAuth2.0 回调地址，需与登记的可信域名一致 */
    @NotBlank
    private String redirectUri;

    /** 用户服务生成的一次性防 CSRF 状态值 */
    @NotBlank
    private String state;
}
