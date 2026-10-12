package com.oriole.wisepen.questionnaire.domain.entity;

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
        @CompoundIndex(name = "idx_questionnaire_row_resource_user_submit_time", def = "{'resourceId': 1, 'userId': 1, 'submitTime': -1}"),
        @CompoundIndex(name = "idx_questionnaire_row_resource_submit_time", def = "{'resourceId': 1, 'submitTime': -1}")
})
public class TableRowEntity {
    @Id
    private String id;

    private String resourceId;
    private Long userId;
    private Map<String, Object> values;

    @CreatedDate
    private LocalDateTime submitTime;

    @LastModifiedDate
    private LocalDateTime updateTime;
}
