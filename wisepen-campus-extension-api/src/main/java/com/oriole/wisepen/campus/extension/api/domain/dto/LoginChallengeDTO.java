package com.oriole.wisepen.campus.extension.api.domain.dto;

import com.oriole.wisepen.campus.extension.api.enums.ChallengeCompletionMode;
import com.oriole.wisepen.campus.extension.api.enums.LoginChallengeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Provider 要求用户继续完成的登录挑战
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginChallengeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录挑战标识 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "登录挑战标识")
    private String challengeId;

    /** 登录挑战类型，例如二维码扫码、验证码 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "登录挑战类型")
    private LoginChallengeType type;

    /** Challenge 的完成方式 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Challenge 的完成方式")
    private ChallengeCompletionMode completionMode;

    /** 用户完成 Challenge 时需要提供的结构化输入 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "完成 Challenge 所需的结构化输入")
    @Builder.Default
    private List<InputDefinitionDTO> inputs = List.of();

    /** 前端展示 Challenge 所需的物料 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "前端展示 Challenge 所需的物料")
    @Builder.Default
    private List<ChallengeArtifactDTO> artifacts = List.of();

    /** 挑战过期时间；无法准确提供时可为空 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "挑战过期时间")
    private LocalDateTime expireAt;
}
