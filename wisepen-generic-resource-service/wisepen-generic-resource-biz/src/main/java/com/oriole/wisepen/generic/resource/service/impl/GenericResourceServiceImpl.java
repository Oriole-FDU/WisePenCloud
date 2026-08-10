package com.oriole.wisepen.generic.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.generic.resource.api.constant.GenericResourceConstants;
import com.oriole.wisepen.generic.resource.api.domain.dto.req.GenericResourceUploadInitRequest;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceFileInfoResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceSourceFileDownloadResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceUploadInitResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceUploadStatusResponse;
import com.oriole.wisepen.generic.resource.api.enums.GenericResourceStatusEnum;
import com.oriole.wisepen.generic.resource.domain.entity.GenericResourceFileEntity;
import com.oriole.wisepen.generic.resource.exception.GenericResourceError;
import com.oriole.wisepen.generic.resource.mq.GenericResourceEventPublisher;
import com.oriole.wisepen.generic.resource.repository.GenericResourceFileRepository;
import com.oriole.wisepen.generic.resource.service.GenericResourceTypeResolver;
import com.oriole.wisepen.generic.resource.service.IGenericResourceService;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateReqDTO;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenericResourceServiceImpl implements IGenericResourceService {

    private final GenericResourceFileRepository genericResourceFileRepository;
    private final GenericResourceTypeResolver resourceTypeResolver;
    private final RemoteStorageService remoteStorageService;
    private final RemoteResourceService remoteResourceService;
    private final GenericResourceEventPublisher eventPublisher;
    private final MongoTemplate mongoTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenericResourceUploadInitResponse initUploadGenericResource(GenericResourceUploadInitRequest request, Long uploaderId, Map<Long, GroupRoleType> uploaderGroupRoles) {
        // 上传入口先判定类型归属，专属服务托管的文档、笔记、AI 资产不能走通用资源链路。
        GenericResourceTypeResolver.ResolvedGenericResourceType resolvedType =
                resourceTypeResolver.resolve(request.getFilename(), request.getExtension());

        String uploadId = IdUtil.fastSimpleUUID();
        UploadInitRespDTO uploadInitRespDTO;
        try {
            uploadInitRespDTO = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                    .md5(request.getMd5())
                    .extension(resolvedType.extension())
                    .scene(StorageSceneEnum.PRIVATE_GENERIC_RESOURCE)
                    .bizTag(uploadId)
                    .expectedSize(request.getExpectedSize())
                    .isNeedCallback(true)
                    .build()).getData();
        } catch (Exception e) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_UPLOAD_URL_APPLY_FAILED, e.getMessage());
        }
        if (uploadInitRespDTO == null || !StringUtils.hasText(uploadInitRespDTO.getObjectKey())) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_UPLOAD_URL_APPLY_FAILED);
        }

        GenericResourceFileEntity entity = GenericResourceFileEntity.builder()
                .uploadId(uploadId)
                .resourceName(request.getFilename())
                .resourceType(resolvedType.resourceType())
                .extension(resolvedType.extension())
                .objectKey(uploadInitRespDTO.getObjectKey())
                .md5(request.getMd5())
                .size(request.getExpectedSize())
                .uploaderId(uploaderId)
                .uploaderGroupRoles(uploaderGroupRoles)
                .mountTargetTagId(request.getMountTargetTagId())
                .status(GenericResourceStatusEnum.UPLOADING)
                .build();
        entity = genericResourceFileRepository.save(entity);

        if (Boolean.TRUE.equals(uploadInitRespDTO.getFlashUploaded())) {
            // 秒传事件可能早于本地上传任务落库被消费，因此秒传场景由当前请求同步补偿完成注册。
            StorageRecordDTO storageRecord = queryStorageRecord(entity, true);
            entity = registerUploadedResource(entity, storageRecord.getMd5(), storageRecord.getSize());
        }

        GenericResourceUploadInitResponse response = BeanUtil.copyProperties(uploadInitRespDTO, GenericResourceUploadInitResponse.class);
        response.setUploadId(entity.getUploadId());
        response.setResourceId(entity.getResourceId());
        response.setStatus(entity.getStatus());
        response.setResourceType(entity.getResourceType());
        response.setExtension(entity.getExtension());
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenericResourceUploadStatusResponse syncGenericResourceUploadStatus(String uploadId, Long operatorUserId) {
        GenericResourceFileEntity entity = genericResourceFileRepository.findById(uploadId)
                .orElseThrow(() -> new ServiceException(GenericResourceError.GENERIC_RESOURCE_UPLOAD_NOT_FOUND));
        if (!Objects.equals(entity.getUploaderId(), operatorUserId)) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_PERMISSION_DENIED);
        }
        assertManagedType(entity);

        if (entity.getStatus() != GenericResourceStatusEnum.AVAILABLE) {
            // 主动刷新只做存储状态补偿；对象仍未上传完成时保持原状态返回。
            StorageRecordDTO storageRecord = queryStorageRecord(entity, false);
            if (storageRecord != null) {
                entity = registerUploadedResource(entity, storageRecord.getMd5(), storageRecord.getSize());
            }
        }
        GenericResourceUploadStatusResponse response = BeanUtil.copyProperties(entity, GenericResourceUploadStatusResponse.class);
        response.setStatus(entity.getStatus());
        return response;
    }

    @Override
    public GenericResourceFileInfoResponse getGenericResourceInfo(String resourceId) {
        GenericResourceFileEntity entity = genericResourceFileRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(GenericResourceError.GENERIC_RESOURCE_NOT_FOUND));
        assertManagedType(entity);
        return GenericResourceFileInfoResponse.builder()
                .resourceId(entity.getResourceId())
                .resourceName(entity.getResourceName())
                .resourceType(entity.getResourceType())
                .extension(entity.getExtension())
                .size(entity.getSize())
                .status(entity.getStatus())
                .build();
    }

    @Override
    public GenericResourceSourceFileDownloadResponse createSourceFileDownloadTicket(String resourceId, Long durationSeconds) {
        GenericResourceFileEntity entity = genericResourceFileRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(GenericResourceError.GENERIC_RESOURCE_NOT_FOUND));
        assertManagedType(entity);
        if (entity.getStatus() != GenericResourceStatusEnum.AVAILABLE || !StringUtils.hasText(entity.getObjectKey())) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_NOT_READY);
        }

        String downloadUrl;
        try {
            downloadUrl = remoteStorageService.getDownloadUrl(entity.getObjectKey(), durationSeconds).getData();
        } catch (Exception e) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_DOWNLOAD_URL_APPLY_FAILED, e.getMessage());
        }
        if (!StringUtils.hasText(downloadUrl)) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_DOWNLOAD_URL_APPLY_FAILED);
        }

        return GenericResourceSourceFileDownloadResponse.builder()
                .resourceId(entity.getResourceId())
                .resourceName(entity.getResourceName())
                .resourceType(entity.getResourceType())
                .extension(entity.getExtension())
                .size(entity.getSize())
                .downloadUrl(downloadUrl)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleFileUploaded(FileUploadedMessage message) {
        if (message.getScene() != StorageSceneEnum.PRIVATE_GENERIC_RESOURCE) {
            return;
        }

        GenericResourceFileEntity entity = genericResourceFileRepository.findByObjectKey(message.getObjectKey()).orElse(null);
        if (entity == null) {
            if (Boolean.TRUE.equals(message.getFlashUploaded())) {
                // 秒传事件可能先于本地任务保存到达；同步 initUpload 流程会继续完成注册。
                log.debug("generic resource flash upload event skipped. objectKey={} reason=\"upload task not persisted yet\"",
                        message.getObjectKey());
                return;
            }
            eventPublisher.publishFileDeleteEvent(List.of(message.getObjectKey()));
            log.warn("generic resource upload compensated for missing task. objectKey={}", message.getObjectKey());
            return;
        }
        if (entity.getStatus() == GenericResourceStatusEnum.AVAILABLE) {
            log.debug("generic resource upload event skipped. uploadId={} resourceId={} objectKey={} reason=\"already available\"",
                    entity.getUploadId(), entity.getResourceId(), message.getObjectKey());
            return;
        }

        GenericResourceFileEntity completed = registerUploadedResource(entity, message.getMd5(), message.getSize());
        if (completed.getStatus() == GenericResourceStatusEnum.AVAILABLE) {
            log.info("generic resource upload handled. uploadId={} resourceId={} objectKey={} size={}",
                    completed.getUploadId(), completed.getResourceId(), message.getObjectKey(), message.getSize());
        } else {
            log.debug("generic resource upload event skipped. uploadId={} objectKey={} status={} reason=\"registration in progress\"",
                    completed.getUploadId(), message.getObjectKey(), completed.getStatus());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGenericResources(List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        List<GenericResourceFileEntity> entities = genericResourceFileRepository.findByResourceIdIn(resourceIds);
        if (entities.isEmpty()) {
            log.debug("generic resource delete skipped because no records. count={} resourceIds={}",
                    resourceIds.size(), summarizeIds(resourceIds));
            return;
        }

        List<String> objectKeys = entities.stream()
                .map(GenericResourceFileEntity::getObjectKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        eventPublisher.publishFileDeleteEvent(objectKeys);
        genericResourceFileRepository.deleteByResourceIdIn(resourceIds);

        log.info("generic resources deleted. count={} resourceIds={}",
                entities.size(), summarizeIds(resourceIds));
    }

    private GenericResourceFileEntity registerUploadedResource(GenericResourceFileEntity entity, String actualMd5, Long actualSize) {
        if (entity.getStatus() == GenericResourceStatusEnum.AVAILABLE) {
            return entity;
        }

        // 上传事件、秒传同步补偿、前端状态刷新都可能触发注册；用原子状态抢占避免重复创建资源主档。
        CompletionClaim claim = claimResourceRegistration(entity);
        entity = claim.entity();
        if (!claim.acquired()) {
            return entity;
        }

        Long resourceSize = actualSize != null ? actualSize : entity.getSize();
        try {
            String resourceId = remoteResourceService.createResource(ResourceCreateReqDTO.builder()
                    .resourceName(entity.getResourceName())
                    .resourceType(entity.getResourceType())
                    .ownerId(entity.getUploaderId().toString())
                    .ownerGroupRoles(entity.getUploaderGroupRoles())
                    .mountTargetTagId(entity.getMountTargetTagId())
                    .size(resourceSize)
                    .build()).getData();
            if (!StringUtils.hasText(resourceId)) {
                throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_REGISTER_FAILED);
            }

            entity.setResourceId(resourceId);
            if (StringUtils.hasText(actualMd5)) {
                entity.setMd5(actualMd5);
            }
            entity.setSize(resourceSize);
            entity.setStatus(GenericResourceStatusEnum.AVAILABLE);
            GenericResourceFileEntity saved = genericResourceFileRepository.save(entity);
            log.info("generic resource registered. uploadId={} resourceId={} resourceType={} objectKey={}",
                    saved.getUploadId(), saved.getResourceId(), saved.getResourceType(), saved.getObjectKey());
            return saved;
        } catch (Exception e) {
            entity.setStatus(GenericResourceStatusEnum.REGISTERING_RESOURCE_TIMEOUT);
            genericResourceFileRepository.save(entity);
            log.error("generic resource register failed. uploadId={} objectKey={}",
                    entity.getUploadId(), entity.getObjectKey(), e);
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_REGISTER_FAILED, e.getMessage());
        }
    }

    private CompletionClaim claimResourceRegistration(GenericResourceFileEntity entity) {
        // 只有未注册或上次注册失败的任务可以重新抢占；REGISTERING_RESOURCE 表示已有线程或实例在处理。
        Query query = Query.query(Criteria.where("_id").is(entity.getUploadId())
                .and("status").in(List.of(
                        GenericResourceStatusEnum.UPLOADING,
                        GenericResourceStatusEnum.REGISTERING_RESOURCE_TIMEOUT
                )));
        Update update = new Update().set("status", GenericResourceStatusEnum.REGISTERING_RESOURCE);
        GenericResourceFileEntity claimed = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                GenericResourceFileEntity.class
        );
        if (claimed != null) {
            return new CompletionClaim(true, claimed);
        }
        GenericResourceFileEntity latest = genericResourceFileRepository.findById(entity.getUploadId()).orElse(entity);
        return new CompletionClaim(false, latest);
    }

    private StorageRecordDTO queryStorageRecord(GenericResourceFileEntity entity, boolean required) {
        StorageRecordDTO storageRecord;
        try {
            storageRecord = remoteStorageService.getFileRecord(entity.getObjectKey()).getData();
        } catch (Exception e) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_STORAGE_STATUS_GET_FAILED, e.getMessage());
        }
        if (required && storageRecord == null) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_STORAGE_STATUS_GET_FAILED);
        }
        return storageRecord;
    }

    private void assertManagedType(GenericResourceFileEntity entity) {
        if (!GenericResourceConstants.MANAGED_TYPES.contains(entity.getResourceType())) {
            throw new ServiceException(GenericResourceError.CANNOT_SUPPORT_GENERIC_RESOURCE_TYPE);
        }
    }

    private record CompletionClaim(boolean acquired, GenericResourceFileEntity entity) {
    }
}
