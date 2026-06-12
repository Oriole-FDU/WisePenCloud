package com.oriole.wisepen.common.core.domain.enums;

import java.util.Locale;

public enum BusinessDomain {
    FRAMEWORK,
    USER,
    SYSTEM,
    RESOURCE,
    DOCUMENT,
    NOTE,
    SKILL,
    AGENT,
    STORAGE,
    FUDAN_EXTENSION;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
