package com.oriole.wisepen.media.api.domain.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 媒体内容基类 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MediaBase {
    private MediaUploadMeta uploadMeta;
    private MediaStatus mediaStatus;
}
