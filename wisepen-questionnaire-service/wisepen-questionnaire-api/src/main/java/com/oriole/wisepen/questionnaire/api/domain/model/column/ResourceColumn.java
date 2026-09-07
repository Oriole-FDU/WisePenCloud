package com.oriole.wisepen.questionnaire.api.domain.model.column;

import com.oriole.wisepen.questionnaire.api.enums.TableColumnType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ResourceColumn extends TableColumn {
    private List<String> allowedResourceTypes;
    private Boolean multiple;
    private Integer maxResources;

    @Override
    public TableColumnType getType() {
        return TableColumnType.RESOURCE;
    }

    @Override
    public void validateDefinition() {
        if (maxResources != null && maxResources < 0) {
            throw new IllegalArgumentException("maxResources must be greater than or equal to 0");
        }
        if (Boolean.FALSE.equals(multiple) && maxResources != null && maxResources > 1) {
            throw new IllegalArgumentException("maxResources must be less than or equal to 1 when multiple is false");
        }
    }

    @Override
    protected void validateNonEmptyValue(Object value) {
        getResourceIds(value);
    }

    public List<String> getResourceIds(Object value) {
        if (isEmptyValue(value)) {
            return List.of();
        }
        if (Boolean.TRUE.equals(multiple)) {
            if (!(value instanceof Collection<?> resourceIds)) {
                throw new IllegalArgumentException("value must be resourceId collection");
            }
            if (maxResources != null && resourceIds.size() > maxResources) {
                throw new IllegalArgumentException("resource count must be less than or equal to maxResources");
            }
            List<String> selectedResourceIds = new ArrayList<>();
            for (Object resourceId : resourceIds) {
                selectedResourceIds.add(parseResourceId(resourceId));
            }
            return selectedResourceIds;
        }
        return List.of(parseResourceId(value));
    }

    private String parseResourceId(Object resourceId) {
        if (!(resourceId instanceof CharSequence text) || text.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("resourceId must be text");
        }
        return text.toString();
    }
}
