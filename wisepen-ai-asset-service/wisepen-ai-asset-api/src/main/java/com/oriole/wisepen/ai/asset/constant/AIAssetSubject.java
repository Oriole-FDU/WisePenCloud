package com.oriole.wisepen.ai.asset.constant;

import com.oriole.wisepen.common.core.domain.IBusinessSubject;

import java.util.Locale;

public enum AIAssetSubject implements IBusinessSubject {
    SKILL,
    SKILL_VERSION,
    SKILL_ASSET,
    AGENT,
    AGENT_VERSION,
    AGENT_ASSET;

    @Override
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}