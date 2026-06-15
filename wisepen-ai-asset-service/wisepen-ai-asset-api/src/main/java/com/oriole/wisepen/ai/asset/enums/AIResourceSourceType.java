package com.oriole.wisepen.ai.asset.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AIResourceSourceType {
    BY_AGENT(2,"BY_AGENT"),
    MANUAL(1,"MANUAL");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
