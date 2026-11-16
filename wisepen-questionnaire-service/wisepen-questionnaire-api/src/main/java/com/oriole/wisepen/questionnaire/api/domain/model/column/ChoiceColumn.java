package com.oriole.wisepen.questionnaire.api.domain.model.column;

import com.oriole.wisepen.questionnaire.api.enums.TableColumnType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ChoiceColumn extends TableColumn {
    private List<OptionDefinition> options;
    private Boolean multiple;
    private Integer minSelections;
    private Integer maxSelections;

    @Override
    public TableColumnType getType() {
        return TableColumnType.CHOICE;
    }

    @Override
    public void validateDefinition() {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("options must not be empty");
        }
        if (multiple == null) {
            throw new IllegalArgumentException("multiple must not be null");
        }
        Set<String> optionIds = new HashSet<>();
        for (OptionDefinition option : options) {
            if (option == null || option.getOptionId() == null || option.getOptionId().isBlank()) {
                throw new IllegalArgumentException("optionId must not be blank");
            }
            if (!optionIds.add(option.getOptionId())) {
                throw new IllegalArgumentException("optionId must be unique");
            }
        }
        if (minSelections != null && minSelections < 0) {
            throw new IllegalArgumentException("minSelections must be greater than or equal to 0");
        }
        if (maxSelections != null && maxSelections < 1) {
            throw new IllegalArgumentException("maxSelections must be greater than or equal to 1");
        }
        if (!multiple && maxSelections != null && maxSelections > 1) {
            throw new IllegalArgumentException("maxSelections must be less than or equal to 1 when multiple is false");
        }
        if (!multiple && minSelections != null && minSelections > 1) {
            throw new IllegalArgumentException("minSelections must be less than or equal to 1 when multiple is false");
        }
        if (minSelections != null && maxSelections != null && minSelections > maxSelections) {
            throw new IllegalArgumentException("minSelections must be less than or equal to maxSelections");
        }
        if (minSelections != null && minSelections > options.size()) {
            throw new IllegalArgumentException("minSelections must be less than or equal to option count");
        }
    }

    @Override
    protected void validateNonEmptyValue(Object value) {
        if (multiple) {
            validateMultipleValue(value);
            return;
        }
        validateSingleValue(value);
    }

    @Override
    protected boolean isEmptyValue(Object value) {
        return super.isEmptyValue(value)
                || Boolean.TRUE.equals(multiple) && value instanceof Collection<?> values && values.isEmpty();
    }

    private void validateSingleValue(Object value) {
        if (!(value instanceof CharSequence optionId)) {
            throw new IllegalArgumentException("value must be optionId");
        }
        if (!validOptionIds().contains(optionId.toString())) {
            throw new IllegalArgumentException("value must be one of optionIds");
        }
    }

    private void validateMultipleValue(Object value) {
        if (!(value instanceof Collection<?> selectedOptionIds)) {
            throw new IllegalArgumentException("value must be optionId collection");
        }
        if (minSelections != null && selectedOptionIds.size() < minSelections) {
            throw new IllegalArgumentException("selected option count must be greater than or equal to minSelections");
        }
        if (maxSelections != null && selectedOptionIds.size() > maxSelections) {
            throw new IllegalArgumentException("selected option count must be less than or equal to maxSelections");
        }

        Set<String> validOptionIds = validOptionIds();
        Set<String> selected = new HashSet<>();
        for (Object selectedOptionId : selectedOptionIds) {
            if (!(selectedOptionId instanceof CharSequence optionId)) {
                throw new IllegalArgumentException("selected optionId must be text");
            }
            String optionIdText = optionId.toString();
            if (!validOptionIds.contains(optionIdText)) {
                throw new IllegalArgumentException("value must only contain optionIds");
            }
            if (!selected.add(optionIdText)) {
                throw new IllegalArgumentException("selected optionId must be unique");
            }
        }
    }

    private Set<String> validOptionIds() {
        Set<String> optionIds = new HashSet<>();
        for (OptionDefinition option : options) {
            optionIds.add(option.getOptionId());
        }
        return optionIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionDefinition {
        private String optionId;
        private Boolean isImage;
        private String content;
        private Boolean other;
    }
}
