package com.oriole.wisepen.questionnaire.api.domain.dto.res;

import com.oriole.wisepen.questionnaire.api.domain.base.QuestionnaireDefinitionBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionnaireVersionInfoResponse extends QuestionnaireDefinitionBase {
    private Integer version;
}
