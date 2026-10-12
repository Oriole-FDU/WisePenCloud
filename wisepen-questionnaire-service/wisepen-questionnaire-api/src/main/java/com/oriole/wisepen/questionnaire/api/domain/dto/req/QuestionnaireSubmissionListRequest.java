package com.oriole.wisepen.questionnaire.api.domain.dto.req;

import com.oriole.wisepen.common.core.domain.enums.list.QueryLogicEnum;
import com.oriole.wisepen.common.core.domain.enums.list.SortDirectionEnum;
import com.oriole.wisepen.questionnaire.api.constant.QuestionnaireValidationMsg;
import com.oriole.wisepen.questionnaire.api.enums.QuestionnaireSubmissionFilterOperator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireSubmissionListRequest {
    @NotBlank(message = QuestionnaireValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;

    @Builder.Default
    @NotNull(message = QuestionnaireValidationMsg.PAGE_NUMBER_NOT_NULL)
    @Min(value = 1, message = QuestionnaireValidationMsg.PAGE_MIN_INVALID)
    private Integer page = 1;

    @Builder.Default
    @NotNull(message = QuestionnaireValidationMsg.SIZE_NOT_NULL)
    @Min(value = 1, message = QuestionnaireValidationMsg.SIZE_MIN_INVALID)
    @Max(value = 100, message = QuestionnaireValidationMsg.SIZE_MAX_INVALID)
    private Integer size = 20;

    private List<Long> userIds;

    @Valid
    @Builder.Default
    private FilterGroup filters = FilterGroup.builder().build();

    @Valid
    @Builder.Default
    private List<SortCondition> sorts = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterGroup {
        @Builder.Default
        @NotNull(message = QuestionnaireValidationMsg.FILTER_LOGIC_NOT_NULL)
        private QueryLogicEnum logic = QueryLogicEnum.AND;

        @Valid
        @Builder.Default
        private List<@NotNull(message = QuestionnaireValidationMsg.FILTER_OPERATOR_NOT_NULL) FilterCondition> conditions = new ArrayList<>();

        @Valid
        @Builder.Default
        private List<FilterGroup> groups = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterCondition {
        @NotBlank(message = QuestionnaireValidationMsg.COLUMN_ID_NOT_BLANK)
        private String columnId;

        @NotNull(message = QuestionnaireValidationMsg.FILTER_OPERATOR_NOT_NULL)
        private QuestionnaireSubmissionFilterOperator operator;

        private Object value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SortCondition {
        @NotBlank(message = QuestionnaireValidationMsg.COLUMN_ID_NOT_BLANK)
        private String columnId;

        @NotNull(message = QuestionnaireValidationMsg.SORT_DIRECTION_NOT_NULL)
        private SortDirectionEnum direction;
    }
}
