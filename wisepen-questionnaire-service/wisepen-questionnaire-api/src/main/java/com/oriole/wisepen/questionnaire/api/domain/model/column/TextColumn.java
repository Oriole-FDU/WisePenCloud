package com.oriole.wisepen.questionnaire.api.domain.model.column;

import com.oriole.wisepen.questionnaire.api.enums.TableColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TextColumn extends TableColumn {
    private Integer minLength;
    private Integer maxLength;

    @Override
    public TableColumnType getType() {
        return TableColumnType.TEXT;
    }

    @Override
    public void validateDefinition() {
        if (minLength != null && minLength < 0) {
            throw new IllegalArgumentException("minLength must be greater than or equal to 0");
        }
        if (maxLength != null && maxLength < 0) {
            throw new IllegalArgumentException("maxLength must be greater than or equal to 0");
        }
        if (minLength != null && maxLength != null && minLength > maxLength) {
            throw new IllegalArgumentException("minLength must be less than or equal to maxLength");
        }
    }

    @Override
    protected void validateNonEmptyValue(Object value) {
        if (!(value instanceof CharSequence text)) {
            throw new IllegalArgumentException("value must be text");
        }
        int length = text.length();
        if (minLength != null && length < minLength) {
            throw new IllegalArgumentException("value length must be greater than or equal to minLength");
        }
        if (maxLength != null && length > maxLength) {
            throw new IllegalArgumentException("value length must be less than or equal to maxLength");
        }
    }
}
