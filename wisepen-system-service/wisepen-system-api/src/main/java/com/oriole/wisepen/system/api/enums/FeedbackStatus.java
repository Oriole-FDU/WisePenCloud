package com.oriole.wisepen.system.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FeedbackStatus {
    PENDING(0, "PENDING"),
    PROCESSING(1, "PROCESSING"),
    RESOLVED(2, "RESOLVED"),
    IGNORED(3, "IGNORED"),
    CLOSED(4, "CLOSED");

    private final int code;

    @EnumValue
    private final String value;
}
