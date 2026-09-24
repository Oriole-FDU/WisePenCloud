package com.oriole.wisepen.questionnaire.api.domain.dto.res;

import com.oriole.wisepen.questionnaire.api.domain.base.QuestionnaireDefinitionBase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class QuestionnaireSubmissionDetailResponse extends QuestionnaireDefinitionBase {
    private String submissionId;
    private Map<String, Object> values;
}
