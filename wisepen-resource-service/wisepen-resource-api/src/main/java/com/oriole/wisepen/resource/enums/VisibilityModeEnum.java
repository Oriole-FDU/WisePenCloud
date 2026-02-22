package com.oriole.wisepen.resource.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 标签可见性模式枚举
 */
@Getter
@AllArgsConstructor
public enum VisibilityModeEnum {

    ALL(0, "ALL"),
    ONLY_ADMIN(1, "ONLY_ADMIN"),
    WHITELIST(2, "WHITELIST"),
    BLACKLIST(3, "BLACKLIST");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;
}