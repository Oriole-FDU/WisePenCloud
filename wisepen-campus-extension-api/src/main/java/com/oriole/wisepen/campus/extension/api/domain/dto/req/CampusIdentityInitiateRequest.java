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
 * 请求 Provider 创建一次校园身份认证任务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusIdentityInitiateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 发起认证的 WisePen 用户 ID */
    @NotNull
    private Long userId;

    /** Provider 编码 */
    @NotBlank
    private String providerCode;

    /** Provider 所需输入 */
    private Map<String, String> inputs;
}
