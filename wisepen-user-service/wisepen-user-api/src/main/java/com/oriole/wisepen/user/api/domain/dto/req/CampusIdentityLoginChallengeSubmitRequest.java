package com.oriole.wisepen.user.api.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 用户服务向校园身份 Provider 转交登录 Challenge 响应的请求
 */
@Data
public class CampusIdentityLoginChallengeSubmitRequest {

    /** Provider 稳定编码 */
    @NotBlank
    private String providerCode;

    /** 扩展服务侧任务标识 */
    @NotBlank
    private String taskId;

    /** 待响应的登录挑战标识 */
    @NotBlank
    private String challengeId;

    /** 用户对挑战的响应，例如验证码 */
    @NotNull
    private Map<String, String> answers;
}
