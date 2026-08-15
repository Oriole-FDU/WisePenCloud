package com.oriole.wisepen.questionnaire.repository;

import com.oriole.wisepen.questionnaire.domain.entity.TableRowEntity;
import com.oriole.wisepen.questionnaire.api.enums.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TableRowRepository extends MongoRepository<TableRowEntity, String> {
    Page<TableRowEntity> findByResourceIdAndUserId(String resourceId, Long userId, Pageable pageable);

    long countByResourceIdAndTableVersionAndUserIdAndStatus(String resourceId, Integer tableVersion, Long userId, SubmissionStatus status);

    Page<TableRowEntity> findByResourceId(String resourceId, Pageable pageable);

    Optional<TableRowEntity> findFirstByResourceIdAndUserIdAndTableVersionAndStatusOrderByUpdateTimeDesc(String resourceId, Long userId, Integer tableVersion, SubmissionStatus status);
}
