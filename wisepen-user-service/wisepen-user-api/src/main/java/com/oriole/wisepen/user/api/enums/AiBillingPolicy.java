package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AiBillingPolicy {
    DISABLED(0),
    REVIEW_REQUIRED(1),
    FREE(2);

    @EnumValue
    @JsonValue
    private final int code;
}
