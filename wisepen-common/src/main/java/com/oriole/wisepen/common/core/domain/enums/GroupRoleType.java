package com.oriole.wisepen.common.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum GroupRoleType {

    OWNER(0, "OWNER"),
    ADMIN(1, "ADMIN"),
    MEMBER(2, "MEMBER"),
    NOT_MEMBER(-1, "NOT_MEMBER");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    public static GroupRoleType getByCode(Integer code) {
        if (code == null) {return null;}
        return Arrays.stream(values())
                .filter(t -> t.getCode() == code)
                .findFirst()
                .orElse(null);
    }
}