package com.oriole.wisepen.ai.asset.repository;

import com.oriole.wisepen.ai.asset.domain.entity.AgentEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends MongoRepository<AgentEntity, String> {
    Optional<AgentEntity> findByResourceId(String resourceId);

    void deleteByResourceIdIn(List<String> resourceIds);

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'version': ?1 } }")
    void updateVersionByResourceId(String resourceId, Integer version);
}
