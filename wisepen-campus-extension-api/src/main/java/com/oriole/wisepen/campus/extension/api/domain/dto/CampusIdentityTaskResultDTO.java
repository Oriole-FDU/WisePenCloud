package com.oriole.wisepen.campus.extension.api.domain.dto;

import com.oriole.wisepen.campus.extension.api.enums.CampusIdentityTaskState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 校园身份认证任务的跨服务标准结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusIdentityTaskResultDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Provider 编码 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Provider 编码")
    private String providerCode;

    /** 发起认证的 WisePen 用户 ID */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "int64", description = "发起认证的用户 ID")
    private Long userId;

    /** 扩展服务侧任务标识 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "扩展服务侧任务标识")
    private String taskId;

    /** 标准化任务状态 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "标准化任务状态")
    private CampusIdentityTaskState state;

    /** 需要用户继续完成的登录挑战（state为 CHALLENGE_REQUIRED 时有值） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "需要用户继续完成的登录挑战")
    private LoginChallengeDTO challenge;

    /** 认证成功后由扩展服务标准化出的身份资料（state为 SUCCESS 时有值） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "认证成功后的标准化身份资料")
    private VerifiedIdentityClaimsDTO claims;

    /** 面向调用方的简短状态说明 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "面向调用方的状态说明")
    private String message;
}
