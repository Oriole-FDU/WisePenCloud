package com.oriole.wisepen.questionnaire.domain.entity;

import com.oriole.wisepen.questionnaire.api.enums.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "questionnaire_table_rows")
@CompoundIndexes({
        @CompoundIndex(name = "idx_questionnaire_row_resource_user", def = "{'resourceId': 1, 'userId': 1, 'updateTime': -1}"),
        @CompoundIndex(name = "idx_questionnaire_row_resource_version_user", def = "{'resourceId': 1, 'tableVersion': 1, 'userId': 1}")
})
public class TableRowEntity {
    @Id
    private String id;

    private String resourceId;
    private Integer tableVersion;
    private Long userId;
    private SubmissionStatus status;
    private Map<String, Object> values;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;

    private LocalDateTime submitTime;
}
