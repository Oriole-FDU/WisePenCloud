package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum IdentityType {
    /** 身份 1:学生 2:老师 3:管理员 */

    STUDENT(1, "STUDENT"),
    TEACHER(2, "TEACHER"),
    OPERATOR(3, "OPERATOR"),;

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;
}