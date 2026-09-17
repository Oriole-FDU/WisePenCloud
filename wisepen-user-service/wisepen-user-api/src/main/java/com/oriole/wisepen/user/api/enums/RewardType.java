package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RewardType {
    TOKEN("TOKEN"),
    COIN("COIN"),
    NONE("NONE");

    @EnumValue
    @JsonValue
    private final String value;
}
