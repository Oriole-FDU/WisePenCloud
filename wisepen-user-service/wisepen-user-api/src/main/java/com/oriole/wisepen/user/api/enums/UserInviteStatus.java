package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserInviteStatus {
    BOUND("BOUND"),
    REWARDED("REWARDED");

    @EnumValue
    @JsonValue
    private final String value;
}
