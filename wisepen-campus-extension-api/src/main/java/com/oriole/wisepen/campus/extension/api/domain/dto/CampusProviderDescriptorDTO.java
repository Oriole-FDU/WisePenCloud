package com.oriole.wisepen.campus.extension.api.domain.dto;

import com.oriole.wisepen.campus.extension.api.enums.CampusAuthProtocol;
import com.oriole.wisepen.campus.extension.api.enums.LoginChallengeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 校园 Provider 单项能力描述，供前端渲染登录或身份认证入口。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusProviderDescriptorDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Provider 编码，前端发起登录/认证时原样回传 */
    private String providerCode;

    /** 展示给前端的人类可读名称 */
    private String displayName;

    /** 学校中文展示名称 */
    private String universityName;

    /** 该入口使用的协议：OAUTH2 跳转授权，CLASSIC 表单登录/认证 */
    private CampusAuthProtocol protocol;

    /** 经典协议所需的表单输入；OAuth2 时为空 */
    private List<InputDefinitionDTO> initiateInputs;

    /** 经典协议可能返回的登录挑战类型；OAuth2 时为空 */
    private List<LoginChallengeType> supportedChallenges;
}
