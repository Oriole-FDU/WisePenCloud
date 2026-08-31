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
public class QuestionnaireDefinition {
    @NotBlank(message = QuestionnaireValidationMsg.TITLE_NOT_BLANK)
    private String title;

    private String description;

    @Valid
    @NotNull(message = QuestionnaireValidationMsg.PAGES_NOT_NULL)
    private List<QuestionnairePage> pages;

    private String completionMessage;
    private String logicScript;
}
