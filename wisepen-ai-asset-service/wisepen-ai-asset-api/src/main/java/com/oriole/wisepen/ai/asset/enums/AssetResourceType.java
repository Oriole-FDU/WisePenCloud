package com.oriole.wisepen.ai.asset.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AssetResourceType {
    MD(1, "MD", "md"),
    PYTHON_SCRIPT(2, "PYTHON_SCRIPT", "py"),
    TEXT(3, "TEXT", "txt"),
    JSON(4, "JSON", "json"),
    YAML(5, "YAML", "yaml"),
    TOML(6, "TOML", "toml");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;

    private final String extension;
}
