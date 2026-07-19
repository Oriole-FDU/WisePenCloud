package com.oriole.wisepen.system.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FeedbackType {
    BUG_REPORT(1, "BUG_REPORT"),
    SUGGESTION(2, "SUGGESTION"),
    CONSULTATION(3, "CONSULTATION"),
    COMPLAINT(4, "COMPLAINT"),
    OTHER(99, "OTHER");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
