package com.oriole.wisepen.questionnaire.api.domain.dto.req;

import com.oriole.wisepen.questionnaire.api.constant.QuestionnaireValidationMsg;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireSubmissionUpdateRequest {
    @NotBlank(message = QuestionnaireValidationMsg.SUBMISSION_ID_NOT_BLANK)
    private String submissionId;

    @NotBlank(message = QuestionnaireValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;

    private Map<String, Object> values;
}
