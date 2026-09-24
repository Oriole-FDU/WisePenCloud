package com.oriole.wisepen.questionnaire.api.domain.base;

import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnairePageDefinition;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnaireSubmissionPolicy;
import com.oriole.wisepen.questionnaire.api.domain.model.column.TableColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireDefinitionBase {
    private String description;
    private List<TableColumn> columns;
    private List<QuestionnairePageDefinition> pages;
    private String completionMessage;
    private QuestionnaireSubmissionPolicy submissionPolicy;
}
