package com.oriole.wisepen.ai.asset.repository;

import com.oriole.wisepen.ai.asset.domain.entity.BaseVersionBundleEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

/**
 * skill / agent 版本包仓库的公共查询，由两个具名仓库各自绑定集合
 */
@NoRepositoryBean
public interface BaseVersionBundleRepository<T extends BaseVersionBundleEntity> extends MongoRepository<T, String> {

    Optional<T> findByResourceIdAndVersion(String resourceId, Integer version);

    List<T> findByResourceId(String resourceId);

    @Query("{ 'assets.objectKey': ?0 }")
    Optional<T> findFirstByAssetObjectKey(String objectKey);

    void deleteByResourceIdIn(List<String> resourceIds);
}
