package com.oriole.wisepen.generic.resource.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GenericResourceStatusEnum {
    UPLOADING(1, "UPLOADING"),
    REGISTERING_RESOURCE(2, "REGISTERING_RESOURCE"),
    AVAILABLE(3, "AVAILABLE"),
    REGISTERING_RESOURCE_TIMEOUT(4, "REGISTERING_RESOURCE_TIMEOUT");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
