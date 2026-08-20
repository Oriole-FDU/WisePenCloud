package com.oriole.wisepen.media.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.media.api.constant.MediaConstants;
import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.domain.dto.req.MediaUploadInitRequest;
import com.oriole.wisepen.media.api.domain.dto.res.MediaInfoResponse;
import com.oriole.wisepen.media.api.domain.dto.res.MediaUploadInitResponse;
import com.oriole.wisepen.media.api.domain.mq.MediaProcessTaskMessage;
import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import com.oriole.wisepen.media.domain.entity.MediaInfoEntity;
import com.oriole.wisepen.media.domain.entity.MediaWatermarkSessionEntity;
import com.oriole.wisepen.media.exception.MediaError;
import com.oriole.wisepen.media.mq.KafkaMediaEventPublisher;
import com.oriole.wisepen.media.repository.MediaInfoRepository;
import com.oriole.wisepen.media.repository.MediaWatermarkSessionRepository;
import com.oriole.wisepen.media.service.IMediaProcessService;
import com.oriole.wisepen.media.service.IMediaService;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements IMediaService {

    private static final CopyOptions IGNORE_NULL_COPY_OPTIONS = CopyOptions.create().ignoreNullValue();

    private final MediaInfoRepository mediaInfoRepository;
    private final MediaWatermarkSessionRepository watermarkSessionRepository;
    private final KafkaMediaEventPublisher eventPublisher;
    private final RemoteStorageService remoteStorageService;
    private final IMediaProcessService mediaProcessService;

    @Override
    public MediaUploadInitResponse initUploadMedia(MediaUploadInitRequest request, Long uploaderId,
                                                   Map<Long, GroupRoleType> uploaderGroupRoles) {
        ResourceType resourceType = MediaConstants.resolveResourceType(request.getExtension());
        if (resourceType == null) {
            throw new ServiceException(MediaError.CANNOT_SUPPORT_FILE_TYPE);
        }

        String mediaId = IdUtil.fastSimpleUUID();

        UploadInitRespDTO uploadInitResp;
        try {
            uploadInitResp = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                    .md5(request.getMd5())
                    .extension(request.getExtension())
                    .scene(StorageSceneEnum.PRIVATE_MEDIA)
                    .bizTag(mediaId)
                    .expectedSize(request.getExpectedSize())
                    .isNeedCallback(true)
                    .build()).getData();
        } catch (Exception e) {
            log.warn("media upload init failed. mediaId={} dependency=storageService", mediaId, e);
            throw new ServiceException(MediaError.MEDIA_UPLOAD_URL_APPLY_FAILED, e.getMessage());
        }

        MediaInfoEntity entity = MediaInfoEntity.builder()
                .mediaId(mediaId)
                .ownerId(uploaderId)
                .resourceType(resourceType)
                .originalFilename(request.getFilename())
                .sourceExtension(request.getExtension())
                .sourceObjectKey(uploadInitResp.getObjectKey())
                .mountTargetTagId(request.getMountTargetTagId())
                .uploaderGroupRoles(uploaderGroupRoles)
                .size(request.getExpectedSize())
                .mediaStatus(new MediaStatus(Boolean.TRUE.equals(uploadInitResp.getFlashUploaded())
                        ? MediaStatusEnum.UPLOADED : MediaStatusEnum.UPLOADING))
                .build();
        mediaInfoRepository.save(entity);

        log.info("media upload initialized. mediaId={} objectKey={} flashUploaded={}",
                mediaId, uploadInitResp.getObjectKey(), uploadInitResp.getFlashUploaded());

        if (Boolean.TRUE.equals(uploadInitResp.getFlashUploaded())) {
            eventPublisher.publishProcessTask(MediaProcessTaskMessage.builder()
                    .mediaId(mediaId)
                    .sourceObjectKey(uploadInitResp.getObjectKey())
                    .resourceType(resourceType)
                    .extension(request.getExtension())
                    .build());
        }

        MediaUploadInitResponse response = BeanUtil.copyProperties(uploadInitResp, MediaUploadInitResponse.class);
        response.setMediaId(mediaId);
        return response;
    }

    @Override
    public List<MediaInfoResponse> listPendingMedia(Long uploaderId) {
        List<MediaInfoEntity> entities = mediaInfoRepository.findByOwnerIdAndStatusIn(
                uploaderId,
                List.of(MediaStatusEnum.UPLOADING,
                        MediaStatusEnum.UPLOADED,
                        MediaStatusEnum.REGISTERING_RES,
                        MediaStatusEnum.TRANSFER_TIMEOUT,
                        MediaStatusEnum.REGISTERING_RES_TIMEOUT,
                        MediaStatusEnum.PROBING,
                        MediaStatusEnum.PACKAGING,
                        MediaStatusEnum.FORENSIC_PREPROCESSING,
                        MediaStatusEnum.FAILED)
        );
        return entities.stream().map(entity -> {
            MediaInfoResponse response = BeanUtil.copyProperties(entity, MediaInfoResponse.class);
            if (StrUtil.isNotBlank(entity.getPreviewObjectKey())) {
                response.setCoverUrl(remoteStorageService.getDownloadUrl(entity.getPreviewObjectKey(), null).getData());
            }
            return response;
        }).toList();
    }

    @Override
    public MediaStatus refreshMediaStatus(String mediaId) {
        MediaInfoEntity entity = mediaInfoRepository.findById(mediaId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));

        if (entity.getMediaStatus() == null || entity.getMediaStatus().getStatus() != MediaStatusEnum.UPLOADING) {
            return entity.getMediaStatus();
        }

        StorageRecordDTO storageRecord;
        try {
            storageRecord = remoteStorageService.getFileRecord(entity.getSourceObjectKey()).getData();
        } catch (Exception e) {
            log.warn("media storage status get failed. mediaId={} objectKey={}",
                    mediaId, entity.getSourceObjectKey(), e);
            throw new ServiceException(MediaError.MEDIA_STORAGE_STATUS_GET_FAILED, e.getMessage());
        }

        if (storageRecord != null) {
            BeanUtil.copyProperties(MediaInfoEntity.builder()
                    .sourceObjectKey(storageRecord.getObjectKey())
                    .size(storageRecord.getSize())
                    .mediaStatus(new MediaStatus(MediaStatusEnum.UPLOADED))
                    .build(), entity, IGNORE_NULL_COPY_OPTIONS);
            mediaInfoRepository.save(entity);
            eventPublisher.publishProcessTask(MediaProcessTaskMessage.builder()
                    .mediaId(entity.getMediaId())
                    .sourceObjectKey(entity.getSourceObjectKey())
                    .resourceType(entity.getResourceType())
                    .extension(entity.getSourceExtension())
                    .build());
        }
        return entity.getMediaStatus();
    }

    @Override
    public void retryMediaProcess(String mediaId) {
        MediaInfoEntity entity = mediaInfoRepository.findById(mediaId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        MediaStatusEnum status = entity.getMediaStatus() != null ? entity.getMediaStatus().getStatus() : null;
        if (status == MediaStatusEnum.FAILED) {
            BeanUtil.copyProperties(MediaInfoEntity.builder()
                    .mediaStatus(new MediaStatus(MediaStatusEnum.UPLOADED))
                    .build(), entity, IGNORE_NULL_COPY_OPTIONS);
            mediaInfoRepository.save(entity);
            eventPublisher.publishProcessTask(MediaProcessTaskMessage.builder()
                    .mediaId(entity.getMediaId())
                    .sourceObjectKey(entity.getSourceObjectKey())
                    .resourceType(entity.getResourceType())
                    .extension(entity.getSourceExtension())
                    .build());
        } else if (status == MediaStatusEnum.REGISTERING_RES_TIMEOUT) {
            mediaProcessService.finalizeToReady(mediaId);
        } else {
            throw new ServiceException(MediaError.CANNOT_RETRY_MEDIA_PROCESS_IN_CURRENT_STATE);
        }
        log.info("media retry event dispatched. mediaId={} resourceId={} status={}",
                mediaId, entity.getResourceId(), status);
    }

    @Override
    public void assertMediaUploader(String mediaId, Long uploaderId) {
        MediaInfoEntity entity = mediaInfoRepository.findById(mediaId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        if (!uploaderId.equals(entity.getOwnerId())) {
            throw new ServiceException(MediaError.MEDIA_PERMISSION_DENIED);
        }
    }

    @Override
    public void handleFileUploaded(FileUploadedMessage message) {
        if (message.getScene() != StorageSceneEnum.PRIVATE_MEDIA || Boolean.TRUE.equals(message.getFlashUploaded())) {
            log.debug("media file upload event skipped. objectKey={} scene={} flashUploaded={} reason=\"scene mismatch or flash upload\"",
                    message.getObjectKey(), message.getScene(), message.getFlashUploaded());
            return;
        }

        MediaInfoEntity entity = mediaInfoRepository.findByStorageObjectKey(message.getObjectKey()).orElse(null);
        if (entity == null) {
            eventPublisher.publishFileDeleteEvent(List.of(message.getObjectKey()));
            log.warn("media file upload compensated for missing media. objectKey={}", message.getObjectKey());
            return;
        }

        if (entity.getMediaStatus() == null || entity.getMediaStatus().getStatus() != MediaStatusEnum.UPLOADING) {
            log.debug("media file upload event skipped. mediaId={} resourceId={} objectKey={} reason=\"status mismatch\" status={}",
                    entity.getMediaId(), entity.getResourceId(), message.getObjectKey(),
                    entity.getMediaStatus() != null ? entity.getMediaStatus().getStatus() : null);
            return;
        }

        BeanUtil.copyProperties(MediaInfoEntity.builder()
                .size(message.getSize())
                .mediaStatus(new MediaStatus(MediaStatusEnum.UPLOADED))
                .build(), entity, IGNORE_NULL_COPY_OPTIONS);
        mediaInfoRepository.save(entity);

        eventPublisher.publishProcessTask(MediaProcessTaskMessage.builder()
                .mediaId(entity.getMediaId())
                .sourceObjectKey(entity.getSourceObjectKey())
                .resourceType(entity.getResourceType())
                .extension(entity.getSourceExtension())
                .build());
        log.info("media file upload finished. mediaId={} resourceId={} objectKey={} size={}",
                entity.getMediaId(), entity.getResourceId(), message.getObjectKey(), message.getSize());
    }

    @Override
    public MediaInfoResponse getMediaInfo(String resourceId, ResourceItemResponse resourceInfo) {
        MediaInfoEntity entity = mediaInfoRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        MediaInfoResponse response = BeanUtil.copyProperties(entity, MediaInfoResponse.class);
        if (StrUtil.isNotBlank(entity.getPreviewObjectKey())) {
            response.setCoverUrl(remoteStorageService.getDownloadUrl(entity.getPreviewObjectKey(), null).getData());
        }
        if (resourceInfo != null) {
            resourceInfo.setPreview(response.getCoverUrl());
            response.setResourceInfo(resourceInfo);
        }
        return response;
    }

    @Override
    public String getOriginalDownloadUrl(String resourceId) {
        MediaInfoEntity mediaInfo = mediaInfoRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        if (mediaInfo.getMediaStatus() == null || mediaInfo.getMediaStatus().getStatus() != MediaStatusEnum.READY) {
            throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
        }
        return remoteStorageService.getDownloadUrl(mediaInfo.getSourceObjectKey(), null).getData();
    }

    @Override
    public void deleteMediaByResourceIds(List<String> resourceIds) {
        List<MediaInfoEntity> mediaInfos = mediaInfoRepository.findByResourceIdIn(resourceIds);
        List<MediaWatermarkSessionEntity> sessions = watermarkSessionRepository.findByResourceIdIn(resourceIds);
        Set<String> objectKeys = new LinkedHashSet<>();
        for (MediaInfoEntity mediaInfo : mediaInfos) {
            if (StrUtil.isNotBlank(mediaInfo.getSourceObjectKey())) {
                objectKeys.add(mediaInfo.getSourceObjectKey());
            }
            if (mediaInfo.getSourceHlsObjectKeys() != null) {
                mediaInfo.getSourceHlsObjectKeys().stream()
                        .filter(StrUtil::isNotBlank)
                        .forEach(objectKeys::add);
            }
            if (StrUtil.isNotBlank(mediaInfo.getPreviewObjectKey())) {
                objectKeys.add(mediaInfo.getPreviewObjectKey());
            }
        }
        for (MediaWatermarkSessionEntity session : sessions) {
            if (StrUtil.isNotBlank(session.getPreviewObjectKey())) {
                objectKeys.add(session.getPreviewObjectKey());
            }
            if (StrUtil.isNotBlank(session.getManifestObjectKey())) {
                objectKeys.add(session.getManifestObjectKey());
            }
            if (session.getDeliveryObjectKeys() != null) {
                session.getDeliveryObjectKeys().stream()
                        .filter(StrUtil::isNotBlank)
                        .forEach(objectKeys::add);
            }
        }
        if (!objectKeys.isEmpty()) {
            eventPublisher.publishFileDeleteEvent(new ArrayList<>(objectKeys));
        }
        watermarkSessionRepository.deleteByResourceIdIn(resourceIds);
        mediaInfoRepository.deleteByResourceIdIn(resourceIds);
        log.info("media resources deleted. count={} resourceIds={}", resourceIds.size(), summarizeIds(resourceIds));
    }
}
