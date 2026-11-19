package com.oriole.wisepen.campus.extension.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * OAuth2.0 登录 Provider 能力描述，由扩展服务提供，用户服务用于前端展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ProviderDescriptorDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Provider 编码，由用户服务配置映射到具体扩展服务 */
    private String providerCode;

    /** 展示给前端的人类可读名称 */
    private String displayName;

    /** 学校中文展示名称 */
    private String universityName;
}
