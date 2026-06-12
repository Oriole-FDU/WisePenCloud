package com.oriole.wisepen.ai.asset.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VersionStatus {
    DRAFT(1,"DRAFT"),
    PUBLISHED(2,"PUBLISHED");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
