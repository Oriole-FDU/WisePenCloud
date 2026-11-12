package com.oriole.wisepen.system.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BatchTaskType {
    RESOURCE_FORK("RESOURCE_FORK"),
    USER_REGISTER("USER_REGISTER");

    @EnumValue
    @JsonValue
    private final String value;
}
