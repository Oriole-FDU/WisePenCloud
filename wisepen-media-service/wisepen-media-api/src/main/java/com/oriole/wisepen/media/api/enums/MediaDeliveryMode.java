package com.oriole.wisepen.media.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MediaDeliveryMode {
    IMAGE_PREVIEW(1, "IMAGE_PREVIEW"),
    VIDEO_JIT_HLS(2, "VIDEO_JIT_HLS"),
    VIDEO_AB_HLS(3, "VIDEO_AB_HLS"),
    AUDIO_SOURCE(5, "AUDIO_SOURCE"),
    IMAGE_SOURCE(6, "IMAGE_SOURCE"),
    VIDEO_SOURCE_HLS(7, "VIDEO_SOURCE_HLS");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
