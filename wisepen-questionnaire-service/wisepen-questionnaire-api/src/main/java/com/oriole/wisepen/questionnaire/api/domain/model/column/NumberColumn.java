package com.oriole.wisepen.questionnaire.api.domain.model.column;

import com.oriole.wisepen.questionnaire.api.enums.TableColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class NumberColumn extends TableColumn {
    private BigDecimal min;
    private BigDecimal max;
    private Integer scale;

    @Override
    public TableColumnType getType() {
        return TableColumnType.NUMBER;
    }

    @Override
    public void validateDefinition() {
        if (scale != null && scale < 0) {
            throw new IllegalArgumentException("scale must be greater than or equal to 0");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("min must be less than or equal to max");
        }
    }

    @Override
    protected void validateNonEmptyValue(Object value) {
        BigDecimal number = parseNumber(value);
        if (min != null && number.compareTo(min) < 0) {
            throw new IllegalArgumentException("value must be greater than or equal to min");
        }
        if (max != null && number.compareTo(max) > 0) {
            throw new IllegalArgumentException("value must be less than or equal to max");
        }
        if (scale != null && number.stripTrailingZeros().scale() > scale) {
            throw new IllegalArgumentException("value scale must be less than or equal to scale");
        }
    }

    private BigDecimal parseNumber(Object value) {
        if (value instanceof BigDecimal number) {
            return number;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof CharSequence text) {
            try {
                return new BigDecimal(text.toString());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("value must be number", e);
            }
        }
        throw new IllegalArgumentException("value must be number");
    }
}
