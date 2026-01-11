package com.oriole.wisepen.common.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
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
    @JsonValue
    private final int code;

    private final String desc;

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