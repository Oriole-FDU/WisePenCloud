package com.oriole.wisepen.common.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum IdentityType {

    STUDENT(1, "STUDENT"),
    TEACHER(2, "TEACHER"),
    ADMIN(3, "ADMIN");

    @EnumValue
    private final int code;

    @JsonValue
    private final String value;

    @JsonCreator
    public static IdentityType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value) || String.valueOf(type.code).equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown IdentityType: " + value));
    }

    /**
     * 根据 code 查找枚举
     */
    public static IdentityType getByCode(Integer code) {
        if (code == null) {return null;}
        return Arrays.stream(values())
                .filter(t -> t.getCode() == code)
                .findFirst()
                .orElse(null);
    }
}
