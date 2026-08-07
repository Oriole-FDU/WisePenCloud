package com.oriole.wisepen.questionnaire.service;

import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireCreateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireResponse;

public interface QuestionnaireService {
    String createQuestionnaire(QuestionnaireCreateRequest request);

    QuestionnaireResponse getQuestionnaire(String resourceId);
}
