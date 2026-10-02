package com.oriole.wisepen.questionnaire.api.domain.model;

import com.oriole.wisepen.questionnaire.api.constant.QuestionnaireValidationMsg;
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
public class QuestionnairePage {
    @NotBlank(message = QuestionnaireValidationMsg.PAGE_ID_NOT_BLANK)
    private String pageId;

    private String title;
    private String description;

    @Valid
    @NotNull(message = QuestionnaireValidationMsg.QUESTIONS_NOT_NULL)
    private List<QuestionDefinition> questions;
}
