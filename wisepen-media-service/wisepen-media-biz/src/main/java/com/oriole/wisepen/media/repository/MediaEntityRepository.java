package com.oriole.wisepen.media.repository;

import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import com.oriole.wisepen.media.domain.entity.MediaEntity;
import com.oriole.wisepen.media.domain.entity.MediaEntity.MediaHlsRenditionObjectKeys;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MediaEntityRepository extends MongoRepository<MediaEntity, String> {

    Optional<MediaEntity> findByResourceId(String resourceId);

    List<MediaEntity> findByResourceIdIn(List<String> resourceIds);

    @Query("{ '$or': [ { 'sourceObjectKey': ?0 }, { 'previewObjectKey': ?0 }, { 'hlsObjectKeys.initObjectKey': ?0 }, { 'hlsObjectKeys.segmentObjectKeys': ?0 } ] }")
    Optional<MediaEntity> findByStorageObjectKeyOrPreviewObjectKey(String objectKey);

    @Query("{ 'uploadMeta.uploaderId': ?0, 'mediaStatus.status': { $in: ?1 } }")
    List<MediaEntity> findByUploaderIdAndStatusIn(Long ownerId, List<MediaStatusEnum> statusList);

    @Query("{ 'mediaStatus.status': ?0 }")
    List<MediaEntity> findByStatus(MediaStatusEnum status, Pageable pageable);

    @Query("{ 'mediaStatus.status': ?0, 'updateTime': { '$lte': ?1 } }")
    List<MediaEntity> findStaleByStatus(MediaStatusEnum status, LocalDateTime updateTime, Pageable pageable);

    @Query("{'_id': ?0}")
    @Update("{'$set': {'mediaStatus': ?1}, '$currentDate': {'updateTime': true}}")
    void updateStatusById(String mediaId, MediaStatus status);

    @Query("{'_id': ?0}")
    @Update("{'$set': {'resourceId': ?1}, '$currentDate': {'updateTime': true}}")
    void updateResourceIdById(String mediaId, String resourceId);

    @Query("{'_id': ?0}")
    @Update("{'$set': {'previewObjectKey': ?1, 'hlsObjectKeys': ?2}, '$currentDate': {'updateTime': true}}")
    void updatePreviewObjectKeyById(String mediaId, String previewObjectKey, List<MediaHlsRenditionObjectKeys> hlsObjectKeys);

    void deleteByResourceIdIn(List<String> resourceIds);
}