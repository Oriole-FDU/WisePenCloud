package com.oriole.wisepen.questionnaire.api.constant;

import com.oriole.wisepen.common.core.domain.IBusinessSubject;

import java.util.Locale;

public enum QuestionnaireSubject implements IBusinessSubject {
    QUESTIONNAIRE,
    QUESTIONNAIRE_VERSION;

    @Override
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
