package com.oriole.wisepen.questionnaire.service;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireCreateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireDraftUpdateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireForkRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireVersionInfoResponse;
import com.oriole.wisepen.questionnaire.domain.entity.TableEntity;

import java.util.List;
import java.util.Map;

/**
 * 问卷主档与资源生命周期服务。
 */
public interface IQuestionnaireService {

    String createQuestionnaire(QuestionnaireCreateRequest request, String userId, Map<Long, GroupRoleType> groupRoles);

    void deleteQuestionnaires(List<String> resourceIds);

    void updateQuestionnaireDraft(QuestionnaireDraftUpdateRequest request);

    void publishQuestionnaireVersion(String resourceId);

    QuestionnaireVersionInfoResponse getQuestionnaire(String resourceId, Integer version);

    TableEntity getQuestionnaireInfo(String resourceId);

    QuestionnaireVersionInfoResponse getQuestionnaireDraft(String resourceId, Integer draftVersion);

    String forkQuestionnaire(QuestionnaireForkRequest request, String forkedResourceOwnerId);
}
