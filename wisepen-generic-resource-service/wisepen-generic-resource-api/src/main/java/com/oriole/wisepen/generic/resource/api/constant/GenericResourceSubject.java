package com.oriole.wisepen.generic.resource.api.constant;

import com.oriole.wisepen.common.core.domain.IBusinessSubject;

import java.util.Locale;

public enum GenericResourceSubject implements IBusinessSubject {
    GENERIC_RESOURCE,
    GENERIC_RESOURCE_UPLOAD;

    @Override
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
