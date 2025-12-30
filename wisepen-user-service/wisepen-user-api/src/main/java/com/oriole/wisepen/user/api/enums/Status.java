package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Status {

    NORMAL(1, "NORMAL"),
    UNIDENTIFIED(-1, "UNIDENTIFIED"),
    BANNED(-2, "BANNED");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;
}