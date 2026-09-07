package com.oriole.wisepen.questionnaire.api.domain.model.column;

import com.oriole.wisepen.questionnaire.api.enums.TableColumnType;
import jakarta.validation.Valid;
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
import java.util.UUID;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ChoiceColumn extends TableColumn {
    @Valid
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
        Set<String> optionIds = new HashSet<>();
        for (OptionDefinition option : options) {
            option.ensureOptionId();
            if (!optionIds.add(option.getOptionId())) {
                throw new IllegalArgumentException("optionId must be unique");
            }
            option.validateDefinition();
        }
        if (minSelections != null && minSelections < 0) {
            throw new IllegalArgumentException("minSelections must be greater than or equal to 0");
        }
        if (maxSelections != null && maxSelections < 0) {
            throw new IllegalArgumentException("maxSelections must be greater than or equal to 0");
        }
        if (Boolean.FALSE.equals(multiple) && maxSelections != null && maxSelections > 1) {
            throw new IllegalArgumentException("maxSelections must be less than or equal to 1 when multiple is false");
        }
        if (Boolean.FALSE.equals(multiple) && minSelections != null && minSelections > 1) {
            throw new IllegalArgumentException("minSelections must be less than or equal to 1 when multiple is false");
        }
        if (minSelections != null && maxSelections != null && minSelections > maxSelections) {
            throw new IllegalArgumentException("minSelections must be less than or equal to maxSelections");
        }
    }

    @Override
    protected void validateNonEmptyValue(Object value) {
        if (Boolean.TRUE.equals(multiple)) {
            validateMultipleValue(value);
            return;
        }
        validateSingleValue(value);
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
        @Builder.Default
        private String optionId = UUID.randomUUID().toString();
        private Boolean isImage;
        private String content;
        private Boolean other;

        private void ensureOptionId() {
            if (optionId == null || optionId.isBlank()) {
                optionId = UUID.randomUUID().toString();
            }
        }

        private void validateDefinition() {
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("option content must not be blank");
            }
        }
    }
}
