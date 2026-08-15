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
public class BooleanColumn extends TableColumn {
    private String trueLabel;
    private String falseLabel;

    @Override
    public TableColumnType getType() {
        return TableColumnType.BOOLEAN;
    }

    @Override
    public void validateDefinition() {
    }

    @Override
    protected void validateNonEmptyValue(Object value) {
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("value must be boolean");
        }
    }
}
