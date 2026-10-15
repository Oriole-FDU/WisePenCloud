package com.oriole.wisepen.extension.fudan.constant;

import com.oriole.wisepen.common.core.domain.IBusinessSubject;

import java.util.Locale;

public enum FudanExtensionSubject implements IBusinessSubject {
    UIS;

    @Override
    public String key() {
        return name().toLowerCase(
                Locale.ROOT);
    }
}
