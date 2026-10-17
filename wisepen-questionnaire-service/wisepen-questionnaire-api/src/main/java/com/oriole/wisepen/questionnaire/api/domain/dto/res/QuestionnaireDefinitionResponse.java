package com.oriole.wisepen.questionnaire.api.domain.dto.res;

import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnaireViewDefinition;
import com.oriole.wisepen.questionnaire.api.domain.model.column.TableColumn;
import com.oriole.wisepen.questionnaire.api.enums.TableVersionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireDefinitionResponse {
    private String resourceId;
    private Integer version;
    private TableVersionStatus status;
    private String title;
    private String description;
    private List<TableColumn> columns;
    private QuestionnaireViewDefinition viewDefinition;
}
