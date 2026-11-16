package com.oriole.wisepen.questionnaire.api.domain.dto.res;

import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireInfoResponse {
    private ResourceItemResponse resourceInfo;
    private QuestionnaireVersionInfoResponse questionnaireVersionInfo;
    private Map<Long, UserDisplayBase> authorsDisplay;
}
