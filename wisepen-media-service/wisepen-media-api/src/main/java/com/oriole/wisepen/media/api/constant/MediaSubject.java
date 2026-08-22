package com.oriole.wisepen.media.api.constant;

import com.oriole.wisepen.common.core.domain.IBusinessSubject;

import java.util.Locale;

public enum MediaSubject implements IBusinessSubject {
    MEDIA,
    MEDIA_PROCESS,
    MEDIA_PREVIEW;

    @Override
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
