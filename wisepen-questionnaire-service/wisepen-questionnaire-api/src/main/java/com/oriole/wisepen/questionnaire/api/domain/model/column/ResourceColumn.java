package com.oriole.wisepen.questionnaire.api.domain.model.column;

import com.oriole.wisepen.questionnaire.api.enums.TableColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Collection;
import java.util.List;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ResourceColumn extends TableColumn {
    private List<String> allowedResourceTypes;
    private Integer maxResources;

    @Override
    public TableColumnType getType() {
        return TableColumnType.RESOURCE;
    }

    @Override
    public void validateDefinition() {
        if (maxResources == null || maxResources < 1) {
            throw new IllegalArgumentException("maxResources must be greater than 0");
        }
    }

    @Override
    protected void validateNonEmptyValue(Object value) {
        if (!(value instanceof Collection<?> resourceIds)) {
            throw new IllegalArgumentException("value must be resourceId collection");
        }
        if (resourceIds.size() > maxResources) {
            throw new IllegalArgumentException("resource count must be less than or equal to maxResources");
        }
        for (Object resourceId : resourceIds) {
            if (!(resourceId instanceof CharSequence text) || text.toString().trim().isEmpty()) {
                throw new IllegalArgumentException("resourceId must be text");
            }
        }
    }

    @Override
    protected boolean isEmptyValue(Object value) {
        return super.isEmptyValue(value)
                || value instanceof Collection<?> resources && resources.isEmpty();
    }
}
