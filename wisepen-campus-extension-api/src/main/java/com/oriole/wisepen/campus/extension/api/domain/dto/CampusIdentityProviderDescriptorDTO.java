package com.oriole.wisepen.campus.extension.api.domain.dto;

import com.oriole.wisepen.campus.extension.api.enums.LoginChallengeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Provider 能力描述，由扩展服务提供，用户服务原样转发给前端。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusIdentityProviderDescriptorDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Provider 编码，由用户服务配置映射到具体扩展服务 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Provider 编码")
    private String providerCode;

    /** 展示给前端的人类可读名称 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "展示名称")
    private String displayName;

    /** 学校中文展示名称（认证成功后写入用户资料） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "学校展示名称")
    private String universityName;

    /** 发起认证时该 Provider 需要的结构化输入 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "发起认证所需的结构化输入")
    @Builder.Default
    private List<InputDefinitionDTO> initiateInputs = List.of();

    /** 该 Provider 可能返回的登录挑战类型，例如扫码或验证码 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "支持的登录挑战类型")
    @Builder.Default
    private List<LoginChallengeType> supportedChallenges = List.of();
}
