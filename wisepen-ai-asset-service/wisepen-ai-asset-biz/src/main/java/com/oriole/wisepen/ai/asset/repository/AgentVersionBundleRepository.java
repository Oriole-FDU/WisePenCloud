package com.oriole.wisepen.ai.asset.repository;

import com.oriole.wisepen.ai.asset.domain.entity.AgentVersionBundleEntity;
import com.oriole.wisepen.ai.asset.domain.base.AgentSpecInfoBase;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AgentVersionBundleRepository extends VersionBundleBaseRepository<AgentVersionBundleEntity> {
    @Query("{ '_id': ?0, 'status': 'DRAFT' }")
    @Update("{ '$set': { 'spec': ?1, 'updateTime': ?2 }, '$inc': { 'revision': 1 } }")
    long updateDraftSpec(String id, AgentSpecInfoBase spec, LocalDateTime updateTime);
}
