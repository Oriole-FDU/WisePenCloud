package com.oriole.wisepen.questionnaire.repository;

import com.oriole.wisepen.questionnaire.domain.entity.QuestionnaireViewEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface QuestionnaireViewRepository extends MongoRepository<QuestionnaireViewEntity, String> {
    Optional<QuestionnaireViewEntity> findByResourceIdAndTableVersion(String resourceId, Integer tableVersion);
}
