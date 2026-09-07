package com.oriole.wisepen.questionnaire.api.domain.model.column;

import com.oriole.wisepen.questionnaire.api.enums.TableColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DateTimeColumn extends TableColumn {
    private LocalDateTime min;
    private LocalDateTime max;

    @Override
    public TableColumnType getType() {
        return TableColumnType.DATETIME;
    }

    @Override
    public void validateDefinition() {
        if (min != null && max != null && min.isAfter(max)) {
            throw new IllegalArgumentException("min must be before or equal to max");
        }
    }

    @Override
    protected void validateNonEmptyValue(Object value) {
        LocalDateTime dateTime = parseDateTime(value);
        if (min != null && dateTime.isBefore(min)) {
            throw new IllegalArgumentException("value must be after or equal to min");
        }
        if (max != null && dateTime.isAfter(max)) {
            throw new IllegalArgumentException("value must be before or equal to max");
        }
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof CharSequence text) {
            try {
                return LocalDateTime.parse(text);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("value must be datetime", e);
            }
        }
        throw new IllegalArgumentException("value must be datetime");
    }
}
