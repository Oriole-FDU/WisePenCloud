package com.oriole.wisepen.questionnaire.api.domain.dto.res;

import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnaireDefinition;
import com.oriole.wisepen.questionnaire.api.domain.model.SubmissionPolicy;
import com.oriole.wisepen.questionnaire.api.enums.QuestionnaireVersionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireResponse {
    private String resourceId;
    private Integer version;
    private QuestionnaireVersionStatus status;
    private QuestionnaireDefinition definition;
    private SubmissionPolicy submissionPolicy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
