package com.oriole.wisepen.generic.resource.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GenericResourceStatusEnum {
    UPLOADING(0, "UPLOADING"),
    REGISTERING_RES(1, "REGISTERING_RES"),
    READY(2, "READY"),
    REGISTERING_RES_TIMEOUT(-1, "REGISTERING_RES_TIMEOUT");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
