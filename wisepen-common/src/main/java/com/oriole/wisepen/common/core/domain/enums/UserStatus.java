package com.oriole.wisepen.common.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserStatus {

    NORMAL(1, "NORMAL"),
    UNIDENTIFIED(-1, "UNIDENTIFIED"),
    BANNED(-2, "BANNED");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
