package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DegreeLevel {

    UNDERGRADUATE(1, "UNDERGRADUATE"),
    MASTER(2, "MASTER"),
    DOCTOR(3, "DOCTOR");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;
}