package com.oriole.wisepen.questionnaire.api.domain.model;

import com.oriole.wisepen.questionnaire.api.constant.QuestionnaireValidationMsg;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireSubmissionPolicy {
    private Boolean anonymous;
    private Integer maxSubmissionsPerUser;
    private Boolean draftAllowed;
    private Boolean editableAfterSubmit;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
