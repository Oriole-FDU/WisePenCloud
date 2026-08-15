package com.oriole.wisepen.questionnaire.domain.entity;

import com.oriole.wisepen.questionnaire.api.domain.model.column.TableColumn;
import com.oriole.wisepen.questionnaire.api.enums.TableVersionStatus;
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
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "questionnaire_table_versions")
@CompoundIndex(name = "uk_table_version", def = "{'resourceId': 1, 'version': 1}", unique = true)
public class TableVersionEntity {
    @Id
    private String id;

    private String resourceId;
    private Integer version;
    private TableVersionStatus status;
    private List<TableColumn> columns;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;
}
