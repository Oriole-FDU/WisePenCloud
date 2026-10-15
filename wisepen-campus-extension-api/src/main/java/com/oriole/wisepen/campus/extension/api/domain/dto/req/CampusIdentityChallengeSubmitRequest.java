package com.oriole.wisepen.campus.extension.api.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 向 Provider 提交登录 Challenge 响应的标准请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusIdentityChallengeSubmitRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 发起认证的 WisePen 用户 ID */
    @NotNull
    private Long userId;

    /** Provider 编码 */
    @NotBlank
    private String providerCode;

    /** 扩展服务侧任务标识 */
    @NotBlank
    private String taskId;

    /** 待响应的登录挑战标识 */
    @NotBlank
    private String challengeId;

    /** 用户对挑战的响应 */
    private Map<String, Object> payload;
}
