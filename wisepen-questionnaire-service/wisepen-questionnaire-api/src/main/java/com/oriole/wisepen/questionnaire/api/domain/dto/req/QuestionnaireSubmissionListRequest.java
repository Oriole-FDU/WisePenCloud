package com.oriole.wisepen.questionnaire.api.domain.dto.req;

import com.oriole.wisepen.questionnaire.api.constant.QuestionnaireValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireSubmissionListRequest {
    @NotBlank(message = QuestionnaireValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;

    @Min(1)
    private Integer version;

    @Builder.Default
    @Min(1)
    private Integer page = 1;

    @Builder.Default
    @Min(1)
    @Max(100)
    private Integer size = 20;
}
