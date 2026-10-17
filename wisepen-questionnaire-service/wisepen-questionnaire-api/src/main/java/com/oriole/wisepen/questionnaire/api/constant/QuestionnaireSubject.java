package com.oriole.wisepen.questionnaire.api.constant;

import com.oriole.wisepen.common.core.domain.IBusinessSubject;

import java.util.Locale;

public enum QuestionnaireSubject implements IBusinessSubject {
    TABLE,
    TABLE_VERSION,
    QUESTIONNAIRE_VIEW,
    TABLE_COLUMN,
    SUBMISSION;

    @Override
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
