package com.oriole.wisepen.resource.repository;


import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ResourceItemRepository extends MongoRepository<ResourceItemEntity, String> {

    // 根据资源 ID 查询绑定记录
    Optional<ResourceItemEntity> findByResourceId(String resourceId);

    // 根据 tagId 查找所有关联的资源
    @Query("{ 'groupBinds.tagIds': { $in: ?0 } }")
    List<ResourceItemEntity> findByTagIdsIn(List<String> tagIds);
}