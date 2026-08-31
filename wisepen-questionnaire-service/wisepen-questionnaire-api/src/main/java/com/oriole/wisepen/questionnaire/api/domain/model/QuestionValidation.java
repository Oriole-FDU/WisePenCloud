package com.oriole.wisepen.questionnaire.api.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionValidation {
    private Integer minLength;
    private Integer maxLength;
    private Integer minSelections;
    private Integer maxSelections;
    private BigDecimal minNumber;
    private BigDecimal maxNumber;
    private Integer minRankedOptions;
    private Integer maxRankedOptions;
}
