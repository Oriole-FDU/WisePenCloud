package com.oriole.wisepen.questionnaire.api.validation;

import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireSubmissionListRequest;
import com.oriole.wisepen.questionnaire.api.domain.model.column.ChoiceColumn;
import com.oriole.wisepen.questionnaire.api.domain.model.column.TableColumn;
import com.oriole.wisepen.questionnaire.api.enums.QuestionnaireSubmissionFilterOperator;
import com.oriole.wisepen.questionnaire.api.enums.TableColumnType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class QuestionnaireSubmissionQueryValidator {

    public void validateFilterGroup(QuestionnaireSubmissionListRequest.FilterGroup group,
                                    Map<String, TableColumn> columnsById) {
        if (group == null) {
            return;
        }
        if (group.getLogic() == null) {
            throw invalid("筛选组逻辑不能为空");
        }

        if (group.getConditions() != null) {
            for (QuestionnaireSubmissionListRequest.FilterCondition condition : group.getConditions()) {
                if (condition == null || condition.getColumnId() == null || condition.getColumnId().isBlank()) {
                    throw invalid("筛选题目不能为空");
                }
                TableColumn column = columnsById.get(condition.getColumnId());
                if (column == null) {
                    throw invalid("筛选题目不存在: " + condition.getColumnId());
                }
                QuestionnaireSubmissionFilterOperator operator = condition.getOperator();
                if (operator == null) {
                    throw invalid("筛选操作符不能为空");
                }
                boolean supported = switch (column.getType()) {
                    case BOOLEAN -> operator == QuestionnaireSubmissionFilterOperator.EQUALS;
                    case CHOICE -> operator == QuestionnaireSubmissionFilterOperator.ANY_OF
                            || operator == QuestionnaireSubmissionFilterOperator.ALL_OF;
                    case NUMBER -> operator == QuestionnaireSubmissionFilterOperator.RANGE;
                    default -> false;
                };
                if (!supported) {
                    throw invalid("题目不支持筛选: " + column.getColumnId());
                }
                validateFilterValue(column, operator, condition.getValue());
            }
        }

        if (group.getGroups() != null) {
            for (QuestionnaireSubmissionListRequest.FilterGroup child : group.getGroups()) {
                validateFilterGroup(child, columnsById);
            }
        }
    }

    public void validateSort(List<QuestionnaireSubmissionListRequest.SortCondition> sorts,
                             Map<String, TableColumn> columnsById) {
        if (sorts == null) {
            return;
        }
        for (QuestionnaireSubmissionListRequest.SortCondition sort : sorts) {
            if (sort == null || sort.getColumnId() == null || sort.getColumnId().isBlank()) {
                throw invalid("排序题目不能为空");
            }
            TableColumn column = columnsById.get(sort.getColumnId());
            if (column == null) {
                throw invalid("排序题目不存在: " + sort.getColumnId());
            }
            if (sort.getDirection() == null) {
                throw invalid("排序方向不能为空");
            }
            if (column.getType() != TableColumnType.NUMBER && column.getType() != TableColumnType.DATETIME) {
                throw invalid("题目不支持排序: " + column.getColumnId());
            }
        }
    }

    private void validateFilterValue(TableColumn column, QuestionnaireSubmissionFilterOperator operator, Object value) {
        switch (operator) {
            case EQUALS -> {
                if (!(value instanceof Boolean)) {
                    throw invalid("布尔题筛选值必须是 boolean: " + column.getColumnId());
                }
            }
            case ANY_OF, ALL_OF -> {
                if (!(column instanceof ChoiceColumn choiceColumn)
                        || !(value instanceof Collection<?> values)
                        || values.isEmpty()) {
                    throw invalid("选择题筛选值必须是非空选项集合: " + column.getColumnId());
                }
                Set<String> optionIds = new HashSet<>();
                choiceColumn.getOptions().forEach(option -> optionIds.add(option.getOptionId()));
                for (Object selectedValue : values) {
                    if (!(selectedValue instanceof CharSequence optionId)
                            || !optionIds.contains(optionId.toString())) {
                        throw invalid("选择题筛选值必须是已定义的 optionId: " + column.getColumnId());
                    }
                }
            }
            case RANGE -> {
                if (!(value instanceof Map<?, ?> range)) {
                    throw invalid("数字题范围筛选值必须是对象: " + column.getColumnId());
                }
                Object minValue = range.get("min");
                Object maxValue = range.get("max");
                if ((minValue != null && !(minValue instanceof Number))
                        || (maxValue != null && !(maxValue instanceof Number))) {
                    throw invalid("数字题范围边界必须是数字: " + column.getColumnId());
                }
                Number min = (Number) minValue;
                Number max = (Number) maxValue;
                if (min == null && max == null) {
                    throw invalid("数字题范围至少需要一个边界: " + column.getColumnId());
                }
                if (min != null && max != null
                        && new BigDecimal(min.toString()).compareTo(new BigDecimal(max.toString())) > 0) {
                    throw invalid("数字题范围的 min 不能大于 max: " + column.getColumnId());
                }
            }
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

}
