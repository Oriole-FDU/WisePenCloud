package com.oriole.wisepen.system.api.constant;

import com.oriole.wisepen.common.core.domain.IBusinessSubject;

import java.util.Locale;

public enum SystemSubject implements IBusinessSubject {
    MAIL,
    FEEDBACK,
    BATCH_TASK,
    BATCH_TASK_RESOURCE_FORK,
    BUSINESS_CONFIG,
    ;

    @Override
    public String key() {
        return name().toLowerCase(
                Locale.ROOT);
    }
}
