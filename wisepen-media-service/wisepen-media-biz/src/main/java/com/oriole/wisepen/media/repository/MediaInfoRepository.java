package com.oriole.wisepen.media.repository;

import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import com.oriole.wisepen.media.domain.entity.MediaInfoEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MediaInfoRepository extends MongoRepository<MediaInfoEntity, String> {

    Optional<MediaInfoEntity> findByResourceId(String resourceId);

    List<MediaInfoEntity> findByResourceIdIn(List<String> resourceIds);

    void deleteByResourceIdIn(List<String> resourceIds);

    @Query("{ '$or': [ { 'sourceObjectKey': ?0 }, { 'previewObjectKey': ?0 }, { 'sourceHlsObjectKeys': ?0 } ] }")
    Optional<MediaInfoEntity> findByStorageObjectKey(String objectKey);

    @Query("{ 'ownerId': ?0, 'mediaStatus.status': { $in: ?1 } }")
    List<MediaInfoEntity> findByOwnerIdAndStatusIn(Long ownerId, List<MediaStatusEnum> statusList);

    @Query("{ 'mediaStatus.status': ?0 }")
    List<MediaInfoEntity> findByStatus(MediaStatusEnum status, Pageable pageable);

    @Query("{ 'mediaStatus.status': ?0, 'updateTime': { '$lte': ?1 } }")
    List<MediaInfoEntity> findStaleByStatus(MediaStatusEnum status, LocalDateTime updateTime, Pageable pageable);

    @Query("{'_id': ?0}")
    @Update("{'$set': {'mediaStatus': ?1}, '$currentDate': {'updateTime': true}}")
    void updateStatusById(String mediaId, MediaStatus status);

    @Query("{'_id': ?0, 'mediaStatus.status': ?1}")
    @Update("{'$set': {'mediaStatus': ?2}, '$currentDate': {'updateTime': true}}")
    long updateStatusIfCurrent(String mediaId, MediaStatusEnum expectedStatus, MediaStatus status);

    @Query("{'_id': ?0, 'mediaStatus.status': ?1, 'updateTime': {'$lte': ?2}}")
    @Update("{'$set': {'mediaStatus': ?3}, '$currentDate': {'updateTime': true}}")
    long updateStaleStatus(String mediaId, MediaStatusEnum expectedStatus, LocalDateTime updateTime, MediaStatus status);

    @Query("{'_id': ?0, 'mediaStatus.status': ?1}")
    @Update("{'$set': {'mediaStatus': ?2, 'size': ?3, 'sourceObjectKey': ?4}, '$currentDate': {'updateTime': true}}")
    long markUploadedIfCurrent(String mediaId, MediaStatusEnum expectedStatus,
                               MediaStatus status, Long size, String sourceObjectKey);

    @Query("{'_id': ?0, 'mediaStatus.status': ?1, 'resourceId': null}")
    @Update("{'$set': {'resourceId': ?2}, '$currentDate': {'updateTime': true}}")
    long updateResourceIdIfCurrent(String mediaId, MediaStatusEnum expectedStatus, String resourceId);

    @Query("{'_id': ?0}")
    @Update("{'$set': {'sourceHlsPrefix': ?1, 'sourceHlsObjectKeys': ?2, 'previewObjectKey': ?3, 'durationMs': ?4, 'width': ?5, 'height': ?6}, '$currentDate': {'updateTime': true}}")
    void updatePackagingResultById(String mediaId,
                                   String sourceHlsPrefix,
                                   List<String> sourceHlsObjectKeys,
                                   String previewObjectKey,
                                   Long durationMs,
                                   Integer width,
                                   Integer height);
}
