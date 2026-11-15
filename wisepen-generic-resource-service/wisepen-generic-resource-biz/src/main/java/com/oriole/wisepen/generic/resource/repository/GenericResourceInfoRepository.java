package com.oriole.wisepen.generic.resource.repository;

import com.oriole.wisepen.generic.resource.api.enums.GenericResourceStatusEnum;
import com.oriole.wisepen.generic.resource.domain.entity.GenericResourceInfoEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GenericResourceInfoRepository extends MongoRepository<GenericResourceInfoEntity, String> {

    Optional<GenericResourceInfoEntity> findByObjectKey(String objectKey);

    Optional<GenericResourceInfoEntity> findByResourceId(String resourceId);

    @Query("{ 'status': ?0 }")
    List<GenericResourceInfoEntity> findByStatus(GenericResourceStatusEnum status, Pageable pageable);

    @Query("{ 'status': { '$in': ?0 }, 'updateTime': { '$lte': ?1 } }")
    List<GenericResourceInfoEntity> findStaleByStatusIn(List<GenericResourceStatusEnum> statuses,
                                                      LocalDateTime cutoff, Pageable pageable);

    List<GenericResourceInfoEntity> findByResourceIdIn(List<String> resourceIds);

    @Query("{'_id': ?0, 'status': ?1}")
    @Update("{'$set': {'status': ?2}, '$currentDate': {'updateTime': true}}")
    long updateStatusIfCurrent(String genericResourceId, GenericResourceStatusEnum expectedStatus,
                               GenericResourceStatusEnum status);

    @Query("{'_id': ?0, 'status': ?1}")
    @Update("{'$set': {'status': 'READY', 'resourceId': ?2, 'size': ?3}, '$currentDate': {'updateTime': true}}")
    long markReadyIfCurrent(String genericResourceId, GenericResourceStatusEnum expectedStatus,
                            String resourceId, Long size);

    @Query("{'_id': ?0, 'status': 'REGISTERING_RES', 'updateTime': {'$lte': ?1}}")
    @Update("{'$set': {'status': 'REGISTERING_RES_TIMEOUT'}, '$currentDate': {'updateTime': true}}")
    long expireRegistration(String genericResourceId, LocalDateTime cutoff);

    void deleteByResourceIdIn(List<String> resourceIds);
}
