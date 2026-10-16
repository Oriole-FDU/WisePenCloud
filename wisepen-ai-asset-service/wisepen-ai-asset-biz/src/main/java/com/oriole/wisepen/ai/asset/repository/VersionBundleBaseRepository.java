package com.oriole.wisepen.ai.asset.repository;

import com.oriole.wisepen.ai.asset.domain.base.AssetInfoBase;
import com.oriole.wisepen.ai.asset.domain.entity.VersionBundleBaseEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.data.repository.NoRepositoryBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface VersionBundleBaseRepository<T extends VersionBundleBaseEntity<T>> extends MongoRepository<T, String> {

    Optional<T> findByResourceIdAndVersion(String resourceId, Integer version);

    List<T> findByResourceId(String resourceId);

    @Query("{ '$or': [{ 'assets.objectKey': ?0 }, { 'pendingAssets.objectKey': ?0 }] }")
    Optional<T> findFirstByAssetObjectKey(String objectKey);

    // 比较读取时的快照，避免并发上传回调、取消和删除覆盖彼此的文件列表。
    @Query("{ '_id': ?0, 'status': 'DRAFT', 'revision': ?1 }")
    @Update("{ '$set': { 'assets': ?2, 'pendingAssets': ?3, 'updateTime': ?4 }, '$inc': { 'revision': 1 } }")
    long updateDraftAssets(String id, Long revision, List<AssetInfoBase> assets,
                           List<AssetInfoBase> pendingAssets, LocalDateTime updateTime);

    @Query("{ '_id': ?0, 'status': 'DRAFT', 'revision': ?1 }")
    @Update("{ '$set': { 'status': 'PUBLISHED', 'updateTime': ?2 }, '$inc': { 'revision': 1 } }")
    long publishDraft(String id, Long revision, LocalDateTime updateTime);

    void deleteByResourceIdIn(List<String> resourceIds);
}
