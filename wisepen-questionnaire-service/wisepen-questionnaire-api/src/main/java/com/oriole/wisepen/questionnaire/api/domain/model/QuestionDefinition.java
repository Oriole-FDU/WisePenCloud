package com.oriole.wisepen.questionnaire.api.domain.model;

import com.oriole.wisepen.questionnaire.api.constant.QuestionnaireValidationMsg;
import com.oriole.wisepen.questionnaire.api.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDefinition {
    @NotBlank(message = QuestionnaireValidationMsg.QUESTION_ID_NOT_BLANK)
    private String questionId;

    @NotNull(message = QuestionnaireValidationMsg.QUESTION_TYPE_NOT_NULL)
    private QuestionType type;

    @NotBlank(message = QuestionnaireValidationMsg.QUESTION_TITLE_NOT_BLANK)
    private String title;

    private String description;

    private Boolean required;

    @Valid
    private List<OptionDefinition> options;

    private QuestionValidation validation;
}
