package com.oriole.wisepen.questionnaire.repository;

import com.oriole.wisepen.questionnaire.domain.entity.QuestionnaireVersionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionnaireVersionRepository extends MongoRepository<QuestionnaireVersionEntity, String> {
    Optional<QuestionnaireVersionEntity> findByResourceIdAndVersion(String resourceId, Integer version);
}
