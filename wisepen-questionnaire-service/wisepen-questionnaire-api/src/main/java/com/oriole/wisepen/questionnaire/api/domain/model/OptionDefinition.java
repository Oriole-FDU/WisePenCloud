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
public class OptionDefinition {
    @NotBlank(message = QuestionnaireValidationMsg.OPTION_ID_NOT_BLANK)
    private String optionId;

    @NotBlank(message = QuestionnaireValidationMsg.OPTION_LABEL_NOT_BLANK)
    private String label;

    private Boolean other;

    private String imageUrl;
}
