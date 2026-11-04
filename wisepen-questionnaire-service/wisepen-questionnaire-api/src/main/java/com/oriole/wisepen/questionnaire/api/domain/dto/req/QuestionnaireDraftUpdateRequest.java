package com.oriole.wisepen.questionnaire.api.domain.dto.req;

import com.oriole.wisepen.questionnaire.api.constant.QuestionnaireValidationMsg;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnairePageDefinition;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnaireSubmissionPolicy;
import com.oriole.wisepen.questionnaire.api.domain.model.column.TableColumn;
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
public class QuestionnaireDraftUpdateRequest {
    @NotBlank(message = QuestionnaireValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;

    @NotNull(message = QuestionnaireValidationMsg.VERSION_NOT_NULL)
    private Integer version;

    private String description;

    @NotNull(message = QuestionnaireValidationMsg.COLUMNS_NOT_NULL)
    private List<TableColumn> columns;

    private List<QuestionnairePageDefinition> pages;

    private String completionMessage;

    @NotNull(message = QuestionnaireValidationMsg.SUBMISSION_POLICY_NOT_NULL)
    private QuestionnaireSubmissionPolicy submissionPolicy;
}
