package com.oriole.wisepen.questionnaire.api.domain.model;

import com.oriole.wisepen.questionnaire.api.constant.QuestionnaireValidationMsg;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireColumnItem {
    private String columnId;

    private String title;
    private String description;
    private Boolean hidden;
}
