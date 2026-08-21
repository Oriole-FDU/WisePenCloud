package com.oriole.wisepen.generic.resource.repository;

import com.oriole.wisepen.generic.resource.domain.entity.GenericResourceInfoEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenericResourceInfoRepository extends MongoRepository<GenericResourceInfoEntity, String> {

    Optional<GenericResourceInfoEntity> findByObjectKey(String objectKey);

    Optional<GenericResourceInfoEntity> findByResourceId(String resourceId);

    List<GenericResourceInfoEntity> findByResourceIdIn(List<String> resourceIds);

    void deleteByResourceIdIn(List<String> resourceIds);
}
