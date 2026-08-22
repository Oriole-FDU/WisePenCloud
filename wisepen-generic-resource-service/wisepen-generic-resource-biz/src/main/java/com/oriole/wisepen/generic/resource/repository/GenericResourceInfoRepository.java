package com.oriole.wisepen.generic.resource.repository;

import com.oriole.wisepen.generic.resource.domain.entity.GenericResourceInfoEntity;
import com.oriole.wisepen.generic.resource.api.enums.GenericResourceStatusEnum;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenericResourceInfoRepository extends MongoRepository<GenericResourceInfoEntity, String> {

    Optional<GenericResourceInfoEntity> findByObjectKey(String objectKey);

    Optional<GenericResourceInfoEntity> findByResourceId(String resourceId);

    @Query("{ 'status': ?0 }")
    List<GenericResourceInfoEntity> findByStatus(GenericResourceStatusEnum status);

    List<GenericResourceInfoEntity> findByResourceIdIn(List<String> resourceIds);

    @Query("{'_id': ?0}")
    @Update("{'$set': {'status': ?1}}")
    void updateStatusById(String genericResourceId, GenericResourceStatusEnum status);

    void deleteByResourceIdIn(List<String> resourceIds);
}
