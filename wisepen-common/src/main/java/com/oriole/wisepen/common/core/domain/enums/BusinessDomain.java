package com.oriole.wisepen.common.core.domain.enums;

import java.util.Locale;

public enum BusinessDomain {
    FRAMEWORK,
    USER,
    SYSTEM,
    RESOURCE,
    DOCUMENT,
    NOTE,
    AI_RESOURCE,
    WEB_APP,
    STORAGE,
    QUESTIONNAIRE,
    FUDAN_EXTENSION;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
