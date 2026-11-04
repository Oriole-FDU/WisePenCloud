package com.oriole.wisepen.questionnaire.repository;

import com.oriole.wisepen.questionnaire.domain.entity.TableRowEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TableRowRepository extends MongoRepository<TableRowEntity, String> {
    Optional<TableRowEntity> findByIdAndResourceIdAndUserId(String id, String resourceId, Long userId);

    List<TableRowEntity> findByResourceIdAndUserIdOrderBySubmitTimeDesc(String resourceId, Long userId);

    long countByResourceIdAndUserId(String resourceId, Long userId);

    Page<TableRowEntity> findByResourceId(String resourceId, Pageable pageable);

    void deleteByIdAndResourceId(String id, String resourceId);

    long deleteByResourceId(String resourceId);

    void deleteByResourceIdIn(List<String> resourceIds);
}
