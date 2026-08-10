package com.oriole.wisepen.generic.resource.repository;

import com.oriole.wisepen.generic.resource.domain.entity.GenericResourceFileEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenericResourceFileRepository extends MongoRepository<GenericResourceFileEntity, String> {

    Optional<GenericResourceFileEntity> findByObjectKey(String objectKey);

    Optional<GenericResourceFileEntity> findByResourceId(String resourceId);

    List<GenericResourceFileEntity> findByResourceIdIn(List<String> resourceIds);

    void deleteByResourceIdIn(List<String> resourceIds);
}
