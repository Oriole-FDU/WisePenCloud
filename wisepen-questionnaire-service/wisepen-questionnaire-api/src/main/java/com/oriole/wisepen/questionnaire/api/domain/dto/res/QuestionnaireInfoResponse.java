package com.oriole.wisepen.questionnaire.api.domain.dto.res;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireInfoResponse {
    private String resourceId;
    private Integer version;
    private Integer draftVersion;
    private String title;
    private String description;
    private LocalDateTime updateTime;
}
