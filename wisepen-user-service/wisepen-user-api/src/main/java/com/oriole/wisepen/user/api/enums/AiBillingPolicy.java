package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AiBillingPolicy {
    DISABLED(0, "DISABLED"),
    REVIEW_REQUIRED(1, "REVIEW_REQUIRED"),
    FREE(2, "FREE");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
