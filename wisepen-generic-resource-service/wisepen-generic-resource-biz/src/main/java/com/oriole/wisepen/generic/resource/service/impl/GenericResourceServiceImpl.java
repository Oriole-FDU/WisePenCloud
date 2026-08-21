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
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceDownloadResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceFileInfoResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceUploadInitResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceUploadStatusResponse;
import com.oriole.wisepen.generic.resource.api.enums.GenericResourceStatusEnum;
import com.oriole.wisepen.generic.resource.domain.entity.GenericResourceFileEntity;
import com.oriole.wisepen.generic.resource.exception.GenericResourceError;
import com.oriole.wisepen.generic.resource.mq.GenericResourceEventPublisher;
import com.oriole.wisepen.generic.resource.repository.GenericResourceFileRepository;
import com.oriole.wisepen.generic.resource.service.IGenericResourceService;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateReqDTO;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenericResourceServiceImpl implements IGenericResourceService {

    private final GenericResourceFileRepository genericResourceFileRepository;
    private final RemoteStorageService remoteStorageService;
    private final RemoteResourceService remoteResourceService;
    private final GenericResourceEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenericResourceUploadInitResponse initUploadGenericResource(GenericResourceUploadInitRequest request, Long uploaderId, Map<Long, GroupRoleType> uploaderGroupRoles) {
        ResourceType resourceType = ResourceType.fromExtension(request.getExtension());
        if (resourceType == null) {
            resourceType = ResourceType.UNKNOWN;
        }
        if (!GenericResourceConstants.MANAGED_TYPES.contains(resourceType)) {
            throw new ServiceException(GenericResourceError.CANNOT_SUPPORT_GENERIC_RESOURCE_TYPE);
        }
        String extension = StringUtils.hasText(request.getExtension())
                ? request.getExtension().trim().toLowerCase(Locale.ROOT)
                : "";
        while (extension.startsWith(".")) {
            extension = extension.substring(1);
        }

        String genericResourceId = IdUtil.fastSimpleUUID();
        UploadInitRespDTO uploadInitRespDTO;
        try {
            uploadInitRespDTO = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                    .md5(request.getMd5())
                    .extension(extension)
                    .scene(StorageSceneEnum.PRIVATE_GENERIC_RESOURCE)
                    .bizTag(genericResourceId)
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
                .genericResourceId(genericResourceId)
                .resourceName(request.getFilename())
                .resourceType(resourceType)
                .extension(extension)
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
            StorageRecordDTO storageRecord;
            try {
                storageRecord = remoteStorageService.getFileRecord(entity.getObjectKey()).getData();
            } catch (Exception e) {
                throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_STORAGE_STATUS_GET_FAILED, e.getMessage());
            }
            if (storageRecord == null) {
                throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_STORAGE_STATUS_GET_FAILED);
            }
            entity = finalizeToReady(entity, storageRecord.getMd5(), storageRecord.getSize());
        }

        GenericResourceUploadInitResponse response = BeanUtil.copyProperties(uploadInitRespDTO, GenericResourceUploadInitResponse.class);
        response.setGenericResourceId(entity.getGenericResourceId());
        response.setResourceId(entity.getResourceId());
        response.setStatus(entity.getStatus());
        response.setResourceType(entity.getResourceType());
        response.setExtension(entity.getExtension());
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenericResourceUploadStatusResponse syncGenericResourceUploadStatus(String genericResourceId, Long operatorUserId) {
        GenericResourceFileEntity entity = genericResourceFileRepository.findById(genericResourceId)
                .orElseThrow(() -> new ServiceException(GenericResourceError.GENERIC_RESOURCE_UPLOAD_NOT_FOUND));
        if (!Objects.equals(entity.getUploaderId(), operatorUserId)) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_PERMISSION_DENIED);
        }
        assertManagedType(entity);

        if (entity.getStatus() != GenericResourceStatusEnum.READY) {
            // 主动刷新只做存储状态补偿；对象仍未上传完成时保持原状态返回。
            StorageRecordDTO storageRecord;
            try {
                storageRecord = remoteStorageService.getFileRecord(entity.getObjectKey()).getData();
            } catch (Exception e) {
                throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_STORAGE_STATUS_GET_FAILED, e.getMessage());
            }
            if (storageRecord != null) {
                entity = finalizeToReady(entity, storageRecord.getMd5(), storageRecord.getSize());
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
    public GenericResourceDownloadResponse getDownloadUrl(String resourceId, Long durationSeconds) {
        GenericResourceFileEntity entity = genericResourceFileRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(GenericResourceError.GENERIC_RESOURCE_NOT_FOUND));
        assertManagedType(entity);
        if (entity.getStatus() != GenericResourceStatusEnum.READY || !StringUtils.hasText(entity.getObjectKey())) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_NOT_READY);
        }

        String downloadFilename = buildDownloadFilename(entity);
        String contentDisposition = ContentDisposition.attachment()
                .filename(sanitizeDownloadFilename(downloadFilename), StandardCharsets.UTF_8)
                .build().toString();

        String downloadUrl;
        try {
            downloadUrl = remoteStorageService.getDownloadUrl(entity.getObjectKey(), durationSeconds, contentDisposition).getData();
        } catch (Exception e) {
            log.warn("generic resource download url apply failed. resourceId={} objectKey={}",
                    resourceId, entity.getObjectKey(), e);
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_DOWNLOAD_URL_APPLY_FAILED, e.getMessage());
        }
        if (!StringUtils.hasText(downloadUrl)) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_DOWNLOAD_URL_APPLY_FAILED);
        }

        return GenericResourceDownloadResponse.builder()
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
        if (entity.getStatus() == GenericResourceStatusEnum.READY) {
            log.debug("generic resource upload event skipped. genericResourceId={} resourceId={} objectKey={} reason=\"already ready\"",
                    entity.getGenericResourceId(), entity.getResourceId(), message.getObjectKey());
            return;
        }

        GenericResourceFileEntity completed = finalizeToReady(entity, message.getMd5(), message.getSize());
        if (completed.getStatus() == GenericResourceStatusEnum.READY) {
            log.info("generic resource upload handled. genericResourceId={} resourceId={} objectKey={} size={}",
                    completed.getGenericResourceId(), completed.getResourceId(), message.getObjectKey(), message.getSize());
        } else {
            log.debug("generic resource upload event skipped. genericResourceId={} objectKey={} status={} reason=\"registration in progress\"",
                    completed.getGenericResourceId(), message.getObjectKey(), completed.getStatus());
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

    private GenericResourceFileEntity finalizeToReady(GenericResourceFileEntity entity, String actualMd5, Long actualSize) {
        if (entity.getStatus() == GenericResourceStatusEnum.READY) {
            return entity;
        }

        entity.setStatus(GenericResourceStatusEnum.REGISTERING_RES);
        genericResourceFileRepository.save(entity);
        Long resourceSize = actualSize != null ? actualSize : entity.getSize();
        if (!StringUtils.hasText(entity.getResourceId())) {
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
            } catch (Exception e) {
                entity.setStatus(GenericResourceStatusEnum.REGISTERING_RES_TIMEOUT);
                genericResourceFileRepository.save(entity);
                log.error("generic resource register failed. genericResourceId={} objectKey={}",
                        entity.getGenericResourceId(), entity.getObjectKey(), e);
                throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_REGISTER_FAILED, e.getMessage());
            }
        }

        if (StringUtils.hasText(actualMd5)) {
            entity.setMd5(actualMd5);
        }
        entity.setSize(resourceSize);
        entity.setStatus(GenericResourceStatusEnum.READY);
        GenericResourceFileEntity saved = genericResourceFileRepository.save(entity);
        log.info("generic resource registered. genericResourceId={} resourceId={} resourceType={} objectKey={}",
                saved.getGenericResourceId(), saved.getResourceId(), saved.getResourceType(), saved.getObjectKey());
        return saved;
    }

    private void assertManagedType(GenericResourceFileEntity entity) {
        if (!GenericResourceConstants.MANAGED_TYPES.contains(entity.getResourceType())) {
            throw new ServiceException(GenericResourceError.CANNOT_SUPPORT_GENERIC_RESOURCE_TYPE);
        }
    }

    private static String buildDownloadFilename(GenericResourceFileEntity entity) {
        String filename = StringUtils.hasText(entity.getResourceName())
                ? entity.getResourceName().trim()
                : "generic-resource";
        String extension = entity.getExtension();
        if (!StringUtils.hasText(extension) || "unknown".equalsIgnoreCase(extension)) {
            return filename;
        }
        String suffix = "." + extension.trim();
        return filename.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))
                ? filename
                : filename + suffix;
    }

    private static String sanitizeDownloadFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "generic-resource";
        }
        String sanitized = filename.trim().replaceAll("[\\r\\n\\t\\\\/:*?\"<>|]", "_");
        return StringUtils.hasText(sanitized) ? sanitized : "generic-resource";
    }
}
