package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserTaskCode {
    DAILY_CHECK_IN("DAILY_CHECK_IN"),
    STUDENT_VERIFICATION("STUDENT_VERIFICATION"),
    TEACHER_VERIFICATION("TEACHER_VERIFICATION");

    @EnumValue
    @JsonValue
    private final String value;
}
