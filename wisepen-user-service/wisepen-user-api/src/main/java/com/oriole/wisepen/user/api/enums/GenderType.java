package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GenderType {

    MALE(0, "MALE"),
    FEMALE(1, "FEMALE"),
    UNKNOWN(2, "UNKNOWN");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;
}