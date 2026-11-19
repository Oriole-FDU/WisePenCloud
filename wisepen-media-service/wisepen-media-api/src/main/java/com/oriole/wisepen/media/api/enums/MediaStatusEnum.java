package com.oriole.wisepen.media.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MediaStatusEnum {
    UPLOADING(0, "UPLOADING"),
    UPLOADED(1, "UPLOADED"),
    PROCESSING(2, "PROCESSING"),
    REGISTERING_RES(5, "REGISTERING_RES"),
    READY(6, "READY"),
    TRANSFER_TIMEOUT(-1, "TRANSFER_TIMEOUT"),
    REGISTERING_RES_TIMEOUT(-2, "REGISTERING_RES_TIMEOUT"),
    FAILED(-3, "FAILED");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
