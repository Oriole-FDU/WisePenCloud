package com.oriole.wisepen.questionnaire.api.domain.model.column;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.oriole.wisepen.questionnaire.api.enums.TableColumnType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Collection;
import java.util.Map;

@Data
@NoArgsConstructor
@SuperBuilder
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextColumn.class, name = "TEXT"),
        @JsonSubTypes.Type(value = ChoiceColumn.class, name = "CHOICE"),
        @JsonSubTypes.Type(value = NumberColumn.class, name = "NUMBER"),
        @JsonSubTypes.Type(value = DateTimeColumn.class, name = "DATETIME"),
        @JsonSubTypes.Type(value = BooleanColumn.class, name = "BOOLEAN"),
        @JsonSubTypes.Type(value = ResourceColumn.class, name = "RESOURCE")
})
public abstract class TableColumn {
    private String columnId;
    private String name;
    private String description;
    private Boolean required;
    private Object defaultValue;

    public abstract TableColumnType getType();

    public abstract void validateDefinition();

    public final void validateValue(Object value) {
        if (isEmptyValue(value)) {
            if (Boolean.TRUE.equals(required)) {
                throw new IllegalArgumentException("value is required");
            }
            return;
        }
        validateNonEmptyValue(value);
    }

    protected abstract void validateNonEmptyValue(Object value);

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence text) {
            return text.toString().trim().isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }
}
