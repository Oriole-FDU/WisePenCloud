package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserVerificationMode {
    EDU_EMAIL(1, "EDU_EMAIL"),
    CAMPUS_IDENTITY_PROVIDER(2, "CAMPUS_IDENTITY_PROVIDER");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
