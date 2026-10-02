package com.oriole.wisepen.questionnaire.api.domain.dto.req;

import com.oriole.wisepen.questionnaire.api.constant.QuestionnaireValidationMsg;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnaireDefinition;
import com.oriole.wisepen.questionnaire.api.domain.model.SubmissionPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireCreateRequest {
    @NotBlank(message = QuestionnaireValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;

    @Valid
    @NotNull(message = QuestionnaireValidationMsg.DEFINITION_NOT_NULL)
    private QuestionnaireDefinition definition;

    @NotNull(message = QuestionnaireValidationMsg.SUBMISSION_POLICY_NOT_NULL)
    private SubmissionPolicy submissionPolicy;
}
