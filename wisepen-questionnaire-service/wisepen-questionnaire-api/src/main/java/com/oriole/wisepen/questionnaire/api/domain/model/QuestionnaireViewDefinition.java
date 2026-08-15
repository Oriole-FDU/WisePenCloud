package com.oriole.wisepen.questionnaire.api.domain.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireViewDefinition {
    private String title;
    private String description;
    private List<QuestionnairePageDefinition> pages;
    private String completionMessage;
    private SubmissionPolicy submissionPolicy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionnairePageDefinition {
        private Integer pageNumber;
        private String title;
        private String description;
        private List<QuestionnaireColumnItem> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionPolicy {
        private Boolean anonymousAllowed;
        private Integer maxSubmissionsPerUser;
        private Boolean draftAllowed;
        private Boolean editableAfterSubmit;

        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }
}
