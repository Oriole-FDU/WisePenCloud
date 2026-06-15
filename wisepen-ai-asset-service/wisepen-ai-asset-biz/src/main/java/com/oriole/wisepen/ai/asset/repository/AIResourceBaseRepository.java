package com.oriole.wisepen.ai.asset.repository;

import com.oriole.wisepen.ai.asset.domain.entity.AIResourceBaseEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

/**
 * skill / agent 资源主档仓库的公共查询，由两个具名仓库各自绑定集合
 */
@NoRepositoryBean
public interface AIResourceBaseRepository<T extends AIResourceBaseEntity> extends MongoRepository<T, String> {

    Optional<T> findByResourceId(String resourceId);

    List<T> findByResourceIdInAndVersionGreaterThan(List<String> resourceIds, Integer version);

    void deleteByResourceIdIn(List<String> resourceIds);

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'version': ?1 } }")
    void updateVersionByResourceId(String resourceId, Integer version);
}
