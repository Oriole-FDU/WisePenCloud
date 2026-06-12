package com.oriole.wisepen.ai.asset.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AssetUploadStatus {
    UPLOADING(1, "UPLOADING"),
    AVAILABLE(2, "AVAILABLE");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
