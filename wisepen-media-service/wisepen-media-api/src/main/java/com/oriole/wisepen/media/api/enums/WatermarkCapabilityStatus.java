package com.oriole.wisepen.media.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WatermarkCapabilityStatus {
    READY(1, "READY"),
    PREPARING(2, "PREPARING"),
    WEAK(3, "WEAK"),
    UNAVAILABLE(4, "UNAVAILABLE"),
    FAILED(5, "FAILED");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
