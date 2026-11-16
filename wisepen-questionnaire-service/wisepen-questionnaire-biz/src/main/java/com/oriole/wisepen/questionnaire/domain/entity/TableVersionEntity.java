package com.oriole.wisepen.questionnaire.domain.entity;

import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.questionnaire.api.domain.base.QuestionnaireDefinitionBase;
import com.oriole.wisepen.questionnaire.api.enums.TableVersionStatus;
import com.oriole.wisepen.questionnaire.exception.QuestionnaireError;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "questionnaire_table_versions")
@CompoundIndex(name = "uk_table_version", def = "{'resourceId': 1, 'version': 1}", unique = true)
public class TableVersionEntity extends QuestionnaireDefinitionBase {
    @Id
    private String id;

    private String resourceId;

    private Integer version;

    private TableVersionStatus status;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;

    // 问卷发布要求至少包含一道题目，并具备提交策略；pages 仅负责布局，可不配置。
    public void checkCoreDefinitionReady() {
        if (getColumns() == null || getColumns().isEmpty()) {
            throw new ServiceException(QuestionnaireError.TABLE_COLUMN_INVALID, "问卷至少需要一道题目");
        }
        if (getSubmissionPolicy() == null) {
            throw new ServiceException(QuestionnaireError.SUBMISSION_POLICY_INVALID, "问卷提交策略不能为空");
        }
    }
}
