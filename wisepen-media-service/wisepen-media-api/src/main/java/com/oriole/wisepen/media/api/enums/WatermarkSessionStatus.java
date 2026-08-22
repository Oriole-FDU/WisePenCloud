package com.oriole.wisepen.media.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WatermarkSessionStatus {
    PREPARING(1, "PREPARING"),
    READY(2, "READY"),
    FINISHED(3, "FINISHED"),
    EXPIRED(4, "EXPIRED"),
    FAILED(5, "FAILED"),
    CANCELLED(6, "CANCELLED");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
