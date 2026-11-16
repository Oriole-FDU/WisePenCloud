package com.oriole.wisepen.questionnaire.api.domain.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireSubmissionItemResponse {
    private String submissionId;
    private LocalDateTime submitTime;
    private LocalDateTime updateTime;
}
