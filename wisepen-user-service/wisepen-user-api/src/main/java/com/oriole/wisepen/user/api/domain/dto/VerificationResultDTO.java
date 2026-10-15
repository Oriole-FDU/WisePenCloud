package com.oriole.wisepen.user.api.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Deprecated
public class VerificationResultDTO { // 即将弃用
    // 验证是否彻底完成
    private boolean completed;
    // 是否需要前端继续进行某项交互（如扫码）
    private boolean requireAction;
    // 交互所需的物料（比如二维码的 Base64）
    private String actionPayload;
    // 提示信息
    private String message;

    public static VerificationResultDTO success() {
        return VerificationResultDTO.builder().completed(true).build();
    }
    public static VerificationResultDTO pending() {
        return VerificationResultDTO.builder().completed(false).requireAction(false).build();
    }
}
