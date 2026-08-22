package com.oriole.wisepen.media.repository;

import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import com.oriole.wisepen.media.domain.entity.MediaInfoEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

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
    List<MediaInfoEntity> findByStatus(MediaStatusEnum status);

    @Query("{'_id': ?0}")
    @Update("{'$set': {'mediaStatus': ?1}}")
    void updateStatusById(String mediaId, MediaStatus status);

    @Query("{'_id': ?0}")
    @Update("{'$set': {'resourceId': ?1}}")
    void updateResourceIdById(String mediaId, String resourceId);

    @Query("{'_id': ?0}")
    @Update("{'$set': {'sourceHlsPrefix': ?1, 'sourceHlsObjectKeys': ?2, 'previewObjectKey': ?3, 'durationMs': ?4, 'width': ?5, 'height': ?6}}")
    void updatePackagingResultById(String mediaId,
                                   String sourceHlsPrefix,
                                   List<String> sourceHlsObjectKeys,
                                   String previewObjectKey,
                                   Long durationMs,
                                   Integer width,
                                   Integer height);
}
