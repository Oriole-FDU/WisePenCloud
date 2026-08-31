package com.oriole.wisepen.questionnaire.service.impl;

import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireCreateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireResponse;
import com.oriole.wisepen.questionnaire.api.enums.QuestionnaireVersionStatus;
import com.oriole.wisepen.questionnaire.domain.entity.QuestionnaireEntity;
import com.oriole.wisepen.questionnaire.domain.entity.QuestionnaireVersionEntity;
import com.oriole.wisepen.questionnaire.exception.QuestionnaireError;
import com.oriole.wisepen.questionnaire.repository.QuestionnaireRepository;
import com.oriole.wisepen.questionnaire.repository.QuestionnaireVersionRepository;
import com.oriole.wisepen.questionnaire.service.QuestionnaireService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireServiceImpl implements QuestionnaireService {
    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionnaireVersionRepository questionnaireVersionRepository;

    @Override
    public String createQuestionnaire(QuestionnaireCreateRequest request) {
        if (questionnaireRepository.existsById(request.getResourceId())) {
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_ALREADY_EXISTS);
        }

        QuestionnaireEntity questionnaire = QuestionnaireEntity.builder()
                .resourceId(request.getResourceId())
                .version(1)
                .build();
        QuestionnaireVersionEntity version = QuestionnaireVersionEntity.builder()
                .resourceId(request.getResourceId())
                .version(1)
                .status(QuestionnaireVersionStatus.DRAFT)
                .definition(request.getDefinition())
                .submissionPolicy(request.getSubmissionPolicy())
                .build();

        questionnaireRepository.insert(questionnaire);
        questionnaireVersionRepository.insert(version);
        log.info("questionnaire created. resourceId={} version={}", request.getResourceId(), 1);
        return request.getResourceId();
    }

    @Override
    public QuestionnaireResponse getQuestionnaire(String resourceId) {
        QuestionnaireEntity questionnaire = questionnaireRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.QUESTIONNAIRE_NOT_FOUND));
        QuestionnaireVersionEntity version = questionnaireVersionRepository
                .findByResourceIdAndVersion(resourceId, questionnaire.getVersion())
                .orElseThrow(() -> new ServiceException(QuestionnaireError.QUESTIONNAIRE_VERSION_NOT_FOUND));

        return QuestionnaireResponse.builder()
                .resourceId(resourceId)
                .version(version.getVersion())
                .status(version.getStatus())
                .definition(version.getDefinition())
                .submissionPolicy(version.getSubmissionPolicy())
                .createTime(version.getCreateTime())
                .updateTime(version.getUpdateTime())
                .build();
    }

}
