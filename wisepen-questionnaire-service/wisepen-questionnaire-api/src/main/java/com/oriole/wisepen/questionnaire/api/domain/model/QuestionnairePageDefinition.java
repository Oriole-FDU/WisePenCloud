package com.oriole.wisepen.questionnaire.api.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnairePageDefinition {
    private String title;
    private String description;
    private List<QuestionnaireColumnItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionnaireColumnItem {
        private String columnId;
        private Boolean hidden;
    }
}
