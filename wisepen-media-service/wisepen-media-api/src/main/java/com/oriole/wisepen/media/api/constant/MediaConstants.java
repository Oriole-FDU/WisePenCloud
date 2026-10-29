package com.oriole.wisepen.media.api.constant;

import com.oriole.wisepen.resource.enums.ResourceType;

import java.util.Locale;
import java.util.Set;

/**
 * 媒体服务常量。
 */
public final class MediaConstants {

    public static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    public static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "webm", "m4v");

    public static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "wav", "m4a", "aac", "flac", "ogg", "opus");

    /** 本服务允许上传的文件类型白名单 */
    public static final Set<ResourceType> ALLOWED_TYPES = Set.of(
            ResourceType.IMAGE,
            ResourceType.VIDEO,
            ResourceType.AUDIO
    );

    private MediaConstants() {
    }

    public static ResourceType resolveResourceType(String extension) {
        if (extension == null) {
            return null;
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (IMAGE_EXTENSIONS.contains(normalized)) {
            return ResourceType.IMAGE;
        }
        if (VIDEO_EXTENSIONS.contains(normalized)) {
            return ResourceType.VIDEO;
        }
        if (AUDIO_EXTENSIONS.contains(normalized)) {
            return ResourceType.AUDIO;
        }
        return null;
    }
}
