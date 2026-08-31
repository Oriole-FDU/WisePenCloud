package com.oriole.wisepen.questionnaire.api.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionPolicy {
    private Boolean anonymousAllowed;

    private Integer maxSubmissionsPerUser;

    private Boolean draftAllowed;

    private Boolean editableAfterSubmit;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
