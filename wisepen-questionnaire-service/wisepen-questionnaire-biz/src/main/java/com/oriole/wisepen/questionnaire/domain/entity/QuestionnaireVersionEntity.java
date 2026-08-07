package com.oriole.wisepen.questionnaire.domain.entity;

import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnaireDefinition;
import com.oriole.wisepen.questionnaire.api.domain.model.SubmissionPolicy;
import com.oriole.wisepen.questionnaire.api.enums.QuestionnaireVersionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "questionnaire_versions")
@CompoundIndex(name = "uk_questionnaire_version", def = "{'resourceId': 1, 'version': 1}", unique = true)
public class QuestionnaireVersionEntity {
    @Id
    private String id;

    private String resourceId;
    private Integer version;
    private QuestionnaireVersionStatus status;
    private QuestionnaireDefinition definition;
    private SubmissionPolicy submissionPolicy;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;
}
