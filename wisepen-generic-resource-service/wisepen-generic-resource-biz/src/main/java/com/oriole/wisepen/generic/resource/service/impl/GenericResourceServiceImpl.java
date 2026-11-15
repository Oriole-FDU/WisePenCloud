package com.oriole.wisepen.generic.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.file.FileNameUtil;
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
import com.oriole.wisepen.generic.resource.api.domain.base.GenericResourceStatus;
import com.oriole.wisepen.generic.resource.api.domain.dto.req.GenericResourceUploadInitRequest;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceDownloadResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceInfoResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceUploadInitResponse;
import com.oriole.wisepen.generic.resource.api.enums.GenericResourceStatusEnum;
import com.oriole.wisepen.generic.resource.domain.entity.GenericResourceInfoEntity;
import com.oriole.wisepen.generic.resource.exception.GenericResourceError;
import com.oriole.wisepen.generic.resource.mq.GenericResourceEventPublisher;
import com.oriole.wisepen.generic.resource.repository.GenericResourceInfoRepository;
import com.oriole.wisepen.generic.resource.service.IGenericResourceService;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateReqDTO;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
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

    private final GenericResourceInfoRepository genericResourceInfoRepository;
    private final RemoteStorageService remoteStorageService;
    private final RemoteResourceService remoteResourceService;
    private final GenericResourceEventPublisher eventPublisher;

    @Override
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
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_UPLOAD_URL_APPLY_FAILED);
        }
        if (uploadInitRespDTO == null || !StringUtils.hasText(uploadInitRespDTO.getObjectKey())) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_UPLOAD_URL_APPLY_FAILED);
        }

        GenericResourceInfoEntity entity = GenericResourceInfoEntity.builder()
                .genericResourceId(genericResourceId)
                .resourceName(request.getFilename())
                .resourceType(resourceType)
                .extension(extension)
                .objectKey(uploadInitRespDTO.getObjectKey())
                .size(request.getExpectedSize())
                .uploaderId(uploaderId)
                .uploaderGroupRoles(uploaderGroupRoles)
                .mountTargetTagId(request.getMountTargetTagId())
                .status(GenericResourceStatusEnum.UPLOADING)
                .build();
        entity = genericResourceInfoRepository.save(entity);
        log.info("generic resource upload initialized. genericResourceId={} objectKey={} status={}",
                genericResourceId, entity.getObjectKey(), entity.getStatus());

        if (Boolean.TRUE.equals(uploadInitRespDTO.getFlashUploaded())) {
            // 秒传事件可能早于本地上传任务落库被消费，因此秒传场景由当前请求同步补偿完成注册。
            StorageRecordDTO storageRecord;
            try {
                storageRecord = remoteStorageService.getFileRecord(entity.getObjectKey()).getData();
            } catch (Exception e) {
                throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_STORAGE_STATUS_GET_FAILED);
            }
            if (storageRecord == null) {
                throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_STORAGE_STATUS_GET_FAILED);
            }
            entity = finalizeToReady(entity, storageRecord.getSize());
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
    public GenericResourceStatus refreshGenericResourceStatus(String genericResourceId, Long operatorUserId) {
        GenericResourceInfoEntity entity = genericResourceInfoRepository.findById(genericResourceId)
                .orElseThrow(() -> new ServiceException(GenericResourceError.GENERIC_RESOURCE_UPLOAD_NOT_FOUND));
        if (!Objects.equals(entity.getUploaderId(), operatorUserId)) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_PERMISSION_DENIED);
        }
        if (!GenericResourceConstants.MANAGED_TYPES.contains(entity.getResourceType())) {
            throw new ServiceException(GenericResourceError.CANNOT_SUPPORT_GENERIC_RESOURCE_TYPE);
        }

        if (entity.getStatus() != GenericResourceStatusEnum.READY
                && entity.getStatus() != GenericResourceStatusEnum.REGISTERING_RES) {
            // 主动刷新只做存储状态补偿；对象仍未上传完成时保持原状态返回。
            StorageRecordDTO storageRecord;
            try {
                storageRecord = remoteStorageService.getFileRecord(entity.getObjectKey()).getData();
            } catch (Exception e) {
                throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_STORAGE_STATUS_GET_FAILED);
            }
            if (storageRecord != null) {
                entity = finalizeToReady(entity, storageRecord.getSize());
            }
        }
        return new GenericResourceStatus(entity.getStatus());
    }

    @Override
    public GenericResourceInfoResponse getGenericResourceInfo(String resourceId) {
        GenericResourceInfoEntity entity = genericResourceInfoRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(GenericResourceError.GENERIC_RESOURCE_NOT_FOUND));
        if (!GenericResourceConstants.MANAGED_TYPES.contains(entity.getResourceType())) {
            throw new ServiceException(GenericResourceError.CANNOT_SUPPORT_GENERIC_RESOURCE_TYPE);
        }
        return GenericResourceInfoResponse.builder()
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
        GenericResourceInfoEntity entity = genericResourceInfoRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(GenericResourceError.GENERIC_RESOURCE_NOT_FOUND));
        if (!GenericResourceConstants.MANAGED_TYPES.contains(entity.getResourceType())) {
            throw new ServiceException(GenericResourceError.CANNOT_SUPPORT_GENERIC_RESOURCE_TYPE);
        }
        if (entity.getStatus() != GenericResourceStatusEnum.READY || !StringUtils.hasText(entity.getObjectKey())) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_NOT_READY);
        }

        String downloadFilename = StringUtils.hasText(entity.getResourceName())
                ? entity.getResourceName().trim()
                : "";
        if (StringUtils.hasText(downloadFilename)) {
            String extension = entity.getExtension();
            if (StringUtils.hasText(extension) && !"unknown".equalsIgnoreCase(extension)) {
                String suffix = "." + extension.trim();
                if (!StringUtils.endsWithIgnoreCase(downloadFilename, suffix)) {
                    downloadFilename = downloadFilename + suffix;
                }
            }
        }
        downloadFilename = FileNameUtil.cleanInvalid(downloadFilename).replaceAll("\\p{Cntrl}", "");
        String contentDisposition = StringUtils.hasText(downloadFilename)
                ? ContentDisposition.attachment()
                .filename(downloadFilename, StandardCharsets.UTF_8)
                .build().toString()
                : ContentDisposition.attachment().build().toString();

        String downloadUrl;
        try {
            downloadUrl = remoteStorageService.getDownloadUrl(entity.getObjectKey(), durationSeconds, contentDisposition).getData();
        } catch (Exception e) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_DOWNLOAD_URL_APPLY_FAILED);
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
    public void handleFileUploaded(FileUploadedMessage message) {
        if (message.getScene() != StorageSceneEnum.PRIVATE_GENERIC_RESOURCE || Boolean.TRUE.equals(message.getFlashUploaded())) {
            return;
        }

        GenericResourceInfoEntity entity = genericResourceInfoRepository.findByObjectKey(message.getObjectKey()).orElse(null);
        if (entity == null) {
            eventPublisher.publishFileDeleteEvent(List.of(message.getObjectKey()));
            log.warn("generic resource upload compensated for missing task. objectKey={}", message.getObjectKey());
            return;
        }
        finalizeToReady(entity, message.getSize());
    }

    @Override
    public void deleteGenericResources(List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        List<GenericResourceInfoEntity> entities = genericResourceInfoRepository.findByResourceIdIn(resourceIds);
        if (entities.isEmpty()) {
            log.debug("generic resource delete skipped because no records. count={} resourceIds={}",
                    resourceIds.size(), summarizeIds(resourceIds));
            return;
        }

        List<String> objectKeys = entities.stream()
                .map(GenericResourceInfoEntity::getObjectKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        eventPublisher.publishFileDeleteEvent(objectKeys);
        genericResourceInfoRepository.deleteByResourceIdIn(resourceIds);

        log.info("generic resources deleted. count={} resourceIds={}",
                entities.size(), summarizeIds(resourceIds));
    }

    private GenericResourceInfoEntity finalizeToReady(GenericResourceInfoEntity entity, Long actualSize) {
        GenericResourceStatusEnum from = entity.getStatus();
        if (from == GenericResourceStatusEnum.READY || from == GenericResourceStatusEnum.REGISTERING_RES) {
            return entity;
        }

        String genericResourceId = entity.getGenericResourceId();
        Long resourceSize = actualSize != null ? actualSize : entity.getSize();
        // 先进入注册阶段；远程调用失败时保留 REGISTERING_RES_TIMEOUT 供重试。
        if (genericResourceInfoRepository.updateStatusIfCurrent(
                genericResourceId, from, GenericResourceStatusEnum.REGISTERING_RES) == 0) {
            return genericResourceInfoRepository.findById(genericResourceId)
                    .orElseThrow(() -> new ServiceException(GenericResourceError.GENERIC_RESOURCE_NOT_FOUND));
        }
        log.info("generic resource status changed. genericResourceId={} from={} to={}",
                genericResourceId, from, GenericResourceStatusEnum.REGISTERING_RES);

        String resourceId = entity.getResourceId();
        if (!StringUtils.hasText(resourceId)) {
            try {
                resourceId = remoteResourceService.createResource(ResourceCreateReqDTO.builder()
                        .resourceName(entity.getResourceName())
                        .resourceType(entity.getResourceType())
                        .ownerId(entity.getUploaderId().toString())
                        .ownerGroupRoles(entity.getUploaderGroupRoles())
                        .mountTargetTagId(entity.getMountTargetTagId())
                        .size(resourceSize)
                        .build()).getData();
            } catch (Exception e) {
                if (genericResourceInfoRepository.updateStatusIfCurrent(
                        genericResourceId, GenericResourceStatusEnum.REGISTERING_RES,
                        GenericResourceStatusEnum.REGISTERING_RES_TIMEOUT) > 0) {
                    log.info("generic resource status changed. genericResourceId={} from={} to={}",
                            genericResourceId, GenericResourceStatusEnum.REGISTERING_RES, GenericResourceStatusEnum.REGISTERING_RES_TIMEOUT);
                }
                throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_REGISTER_FAILED);
            }
        }

        if (genericResourceInfoRepository.markReadyIfCurrent(
                genericResourceId, GenericResourceStatusEnum.REGISTERING_RES, resourceId, resourceSize) == 0) {
            return genericResourceInfoRepository.findById(genericResourceId)
                    .orElseThrow(() -> new ServiceException(GenericResourceError.GENERIC_RESOURCE_NOT_FOUND));
        }
        entity.setResourceId(resourceId);
        entity.setSize(resourceSize);
        entity.setStatus(GenericResourceStatusEnum.READY);
        log.info("generic resource status changed. genericResourceId={} resourceId={} from={} to={}",
                genericResourceId, resourceId, GenericResourceStatusEnum.REGISTERING_RES, GenericResourceStatusEnum.READY);
        return entity;
    }
}
