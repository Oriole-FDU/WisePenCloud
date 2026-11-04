package com.oriole.wisepen.questionnaire.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.questionnaire.api.domain.base.QuestionnaireDefinitionBase;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireSubmissionListRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireSubmissionDetailResponse;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireSubmissionItemResponse;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireSubmissionResponse;

import java.util.List;
import java.util.Map;

/**
 * 问卷填写、提交与答卷查询服务。
 */
public interface IQuestionnaireSubmissionService {

    QuestionnaireDefinitionBase getQuestionnaireForFilling(String resourceId, Long userId);

    String submitQuestionnaire(String resourceId, Map<String, Object> values, Long userId);

    List<QuestionnaireSubmissionItemResponse> listMySubmissions(String resourceId, Long userId);

    QuestionnaireSubmissionDetailResponse getSubmission(String resourceId, String submissionId, Long userId);

    void updateSubmission(String resourceId, String submissionId, Map<String, Object> values, Long userId);

    PageR<QuestionnaireSubmissionResponse> listAllSubmissions(QuestionnaireSubmissionListRequest request);

    void deleteSubmissions(String resourceId, String submissionId);
}
