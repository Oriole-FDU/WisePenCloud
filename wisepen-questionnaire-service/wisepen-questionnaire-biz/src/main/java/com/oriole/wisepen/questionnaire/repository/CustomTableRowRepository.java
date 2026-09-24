package com.oriole.wisepen.questionnaire.repository;

import com.oriole.wisepen.common.core.domain.enums.list.QueryLogicEnum;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireSubmissionListRequest;
import com.oriole.wisepen.questionnaire.domain.entity.TableRowEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
public class CustomTableRowRepository {

    private final MongoTemplate mongoTemplate;

    public CustomTableRowRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Page<TableRowEntity> listSubmissions(String resourceId, List<Long> userIds,
                                                QuestionnaireSubmissionListRequest.FilterGroup filterGroup,
                                                List<QuestionnaireSubmissionListRequest.SortCondition> sorts,
                                                int page, int size) {
        Query query = Query.query(Criteria.where("resourceId").is(resourceId));
        if (userIds != null && !userIds.isEmpty()) {
            query.addCriteria(Criteria.where("userId").in(userIds));
        }
        Criteria filterCriteria = buildFilterCriteria(filterGroup);
        if (filterCriteria != null) {
            query.addCriteria(filterCriteria);
        }

        Sort sort = sorts == null || sorts.isEmpty() ? Sort.by(Sort.Direction.DESC, "submitTime")
                : Sort.by(sorts.stream().map(condition -> new Sort.Order(condition.getDirection().toSpringDirection(), "values." + condition.getColumnId())).toList());
        Pageable pageable = PageRequest.of(page, size, sort);

        long total = mongoTemplate.count(query, TableRowEntity.class);
        query.with(pageable);
        List<TableRowEntity> rows = mongoTemplate.find(query, TableRowEntity.class);
        return new PageImpl<>(rows, pageable, total);
    }

    private Criteria buildFilterCriteria(QuestionnaireSubmissionListRequest.FilterGroup group) {
        if (group == null) {
            return null;
        }
        List<Criteria> children = new ArrayList<>();
        if (group.getConditions() != null) {
            children.addAll(group.getConditions().stream()
                    .map(this::buildConditionCriteria)
                    .toList());
        }
        if (group.getGroups() != null) {
            group.getGroups().stream()
                    .map(this::buildFilterCriteria)
                    .filter(Objects::nonNull)
                    .forEach(children::add);
        }
        if (children.isEmpty()) {
            return null;
        }
        if (children.size() == 1) {
            return children.get(0);
        }
        return group.getLogic() == QueryLogicEnum.AND
                ? new Criteria().andOperator(children)
                : new Criteria().orOperator(children);
    }

    private Criteria buildConditionCriteria(QuestionnaireSubmissionListRequest.FilterCondition condition) {
        String field = "values." + condition.getColumnId();
        return switch (condition.getOperator()) {
            case EQUALS -> Criteria.where(field).is(condition.getValue());
            case ANY_OF -> Criteria.where(field).in(((Collection<?>) condition.getValue()).stream()
                    .map(Object::toString).toList());
            case ALL_OF -> Criteria.where(field).all(((Collection<?>) condition.getValue()).stream()
                    .map(Object::toString).toList());
            case RANGE -> {
                Map<?, ?> range = (Map<?, ?>) condition.getValue();
                Criteria criteria = Criteria.where(field);
                if (range.get("min") != null) {
                    criteria.gte((Number) range.get("min"));
                }
                if (range.get("max") != null) {
                    criteria.lte((Number) range.get("max"));
                }
                yield criteria;
            }
        };
    }

    public void updateValues(String resourceId, String submissionId, Long userId, Map<String, Object> changedValues) {
        Query query = Query.query(Criteria.where("_id").is(submissionId)
                .and("resourceId").is(resourceId)
                .and("userId").is(userId));
        Update update = new Update();
        changedValues.forEach((columnId, value) -> update.set("values." + columnId, value));
        update.set("updateTime", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, TableRowEntity.class);
    }
}
