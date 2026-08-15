package com.oriole.wisepen.questionnaire.repository;

import com.oriole.wisepen.questionnaire.domain.entity.TableEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

public interface TableRepository extends MongoRepository<TableEntity, String> {
    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'version': ?1 } }")
    void updateVersionByResourceId(String resourceId, Integer version);
}
