package com.oriole.wisepen.questionnaire.domain.entity;

import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnaireViewDefinition;
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
@Document(collection = "questionnaire_views")
@CompoundIndex(name = "uk_questionnaire_view_table_version", def = "{'resourceId': 1, 'tableVersion': 1}", unique = true)
public class QuestionnaireViewEntity {
    @Id
    private String id;

    private String resourceId;
    private Integer tableVersion;
    private QuestionnaireViewDefinition definition;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;
}
