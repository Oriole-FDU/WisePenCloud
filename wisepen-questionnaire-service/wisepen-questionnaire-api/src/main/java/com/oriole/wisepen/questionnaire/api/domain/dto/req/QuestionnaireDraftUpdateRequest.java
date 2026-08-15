package com.oriole.wisepen.questionnaire.api.domain.dto.req;

import com.oriole.wisepen.questionnaire.api.constant.QuestionnaireValidationMsg;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnaireViewDefinition;
import com.oriole.wisepen.questionnaire.api.domain.model.column.TableColumn;
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
public class QuestionnaireDraftUpdateRequest {
    @NotBlank(message = QuestionnaireValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;

    private String title;

    private String description;

    @Valid
    @NotNull(message = QuestionnaireValidationMsg.TABLE_DEFINITION_NOT_NULL)
    private List<TableColumn> columns;

    @Valid
    @NotNull(message = QuestionnaireValidationMsg.QUESTIONNAIRE_VIEW_NOT_NULL)
    private QuestionnaireViewDefinition viewDefinition;
}
