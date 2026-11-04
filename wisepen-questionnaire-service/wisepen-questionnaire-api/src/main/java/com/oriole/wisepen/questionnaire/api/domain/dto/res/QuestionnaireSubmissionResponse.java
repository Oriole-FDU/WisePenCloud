package com.oriole.wisepen.questionnaire.api.domain.dto.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireSubmissionResponse {
    private String submissionId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long userId;
    private Map<String, Object> values;
    private LocalDateTime submitTime;
    private LocalDateTime updateTime;
}
