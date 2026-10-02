package com.oriole.wisepen.questionnaire.api.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionAnswer {
    private String questionId;

    private List<String> selectedOptionIds;

    private String textValue;
    private BigDecimal numberValue;

    private List<String> orderedOptionIds;
}
