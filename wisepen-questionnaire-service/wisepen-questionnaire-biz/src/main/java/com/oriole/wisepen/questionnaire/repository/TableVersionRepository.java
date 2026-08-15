package com.oriole.wisepen.questionnaire.repository;

import com.oriole.wisepen.questionnaire.domain.entity.TableVersionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TableVersionRepository extends MongoRepository<TableVersionEntity, String> {
    Optional<TableVersionEntity> findByResourceIdAndVersion(String resourceId, Integer version);

    List<TableVersionEntity> findByResourceId(String resourceId);
}
