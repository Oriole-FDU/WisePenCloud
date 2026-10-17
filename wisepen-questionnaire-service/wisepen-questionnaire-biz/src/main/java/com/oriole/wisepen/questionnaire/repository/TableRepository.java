package com.oriole.wisepen.questionnaire.repository;

import com.oriole.wisepen.questionnaire.domain.entity.TableEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.time.LocalDateTime;

public interface TableRepository extends MongoRepository<TableEntity, String> {
    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'version': ?1, 'updateTime': ?2 } }")
    void updateVersionByResourceId(String resourceId, Integer version, LocalDateTime updateTime);
}
