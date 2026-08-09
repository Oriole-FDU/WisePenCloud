package com.oriole.wisepen.document.repository;

import com.oriole.wisepen.document.api.domain.base.DocumentStatus;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.domain.entity.DocumentVersionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends MongoRepository<DocumentVersionEntity, String> {

    Optional<DocumentVersionEntity> findByResourceIdAndVersion(String resourceId, Integer version);

    List<DocumentVersionEntity> findByResourceIdIn(List<String> resourceIds);

    List<DocumentVersionEntity> findByResourceId(String resourceId);

    Page<DocumentVersionEntity> findByResourceIdOrderByVersionDesc(String resourceId, Pageable pageable);

    void deleteByResourceIdIn(List<String> resourceIds);

    @Query("{'_id': ?0}")
    @Update("{'$set': {'documentStatus': ?1}}")
    void updateStatusById(String documentId, DocumentStatus status);

    @Query("{'_id': ?0}")
    @Update("{'$set': {'previewObjectKey': ?1}}")
    void updatePreviewObjectKeyById(String documentId, String previewObjectKey);

    @Query("{ 'uploadMeta.uploaderId': ?0, 'documentStatus.status': { $in: ?1 } }")
    List<DocumentVersionEntity> findByUploaderIdAndStatusIn(Long uploaderId, List<DocumentStatusEnum> statusList);

    @Query("{ '$or': [ { 'sourceObjectKey': ?0 }, { 'previewObjectKey': ?0 } ] }")
    Optional<DocumentVersionEntity> findBySourceObjectKeyOrPreviewObjectKey(String objectKey);

    @Query("{ 'documentStatus.status': ?0 }")
    List<DocumentVersionEntity> findByStatus(DocumentStatusEnum status);

    @Query("{'_id': ?0}")
    @Update("{'$set': {'resourceId': ?1}}")
    void updateResourceIdById(String documentId, String resourceId);
}
