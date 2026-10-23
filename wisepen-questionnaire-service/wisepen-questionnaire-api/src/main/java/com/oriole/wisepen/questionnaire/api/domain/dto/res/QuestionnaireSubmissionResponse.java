package com.oriole.wisepen.questionnaire.api.domain.dto.res;

import com.oriole.wisepen.questionnaire.api.enums.SubmissionStatus;
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
    private String resourceId;
    private Integer submittedTableVersion;
    private Integer projectedTableVersion;
    private Long userId;
    private SubmissionStatus status;
    private Map<String, Object> values;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime submitTime;
}
