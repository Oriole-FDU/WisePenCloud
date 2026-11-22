package com.oriole.wisepen.media.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.media.api.constant.MediaConstants;
import com.oriole.wisepen.media.api.domain.base.MediaBase;
import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.domain.base.MediaUploadMeta;
import com.oriole.wisepen.media.api.domain.dto.req.MediaUploadInitRequest;
import com.oriole.wisepen.media.api.domain.dto.res.MediaUploadInitResponse;
import com.oriole.wisepen.media.api.domain.mq.MediaProcessTaskMessage;
import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import com.oriole.wisepen.media.domain.entity.MediaEntity;
import com.oriole.wisepen.media.domain.entity.MediaInfoEntity;
import com.oriole.wisepen.media.domain.entity.MediaMetaEntity;
import com.oriole.wisepen.media.exception.MediaError;
import com.oriole.wisepen.media.mq.KafkaMediaEventPublisher;
import com.oriole.wisepen.media.repository.MediaEntityRepository;
import com.oriole.wisepen.media.repository.MediaInfoRepository;
import com.oriole.wisepen.media.repository.MediaMetaRepository;
import com.oriole.wisepen.media.service.IMediaService;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateReqDTO;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements IMediaService {

    private final MediaEntityRepository mediaEntityRepository;
    private final MediaInfoRepository mediaInfoRepository;
    private final MediaMetaRepository mediaMetaRepository;

    private final KafkaMediaEventPublisher eventPublisher;

    private final RemoteResourceService remoteResourceService;
    private final RemoteStorageService remoteStorageService;

    @Override
    @WithSpan("media.upload.initialize")
    public MediaUploadInitResponse initUploadMedia(MediaUploadInitRequest request, Long uploaderId,
                                                   Map<Long, GroupRoleType> uploaderGroupRoles) {
        String extension = StrUtil.trim(request.getExtension()).toLowerCase(Locale.ROOT);
        ResourceType resourceType = MediaConstants.resolveResourceType(extension);
        if (resourceType == null) {
            throw new ServiceException(MediaError.CANNOT_SUPPORT_FILE_TYPE);
        }

        String mediaId = IdUtil.fastSimpleUUID();

        UploadInitRespDTO uploadInitResp;
        try {
            uploadInitResp = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                    .md5(request.getMd5())
                    .extension(extension)
                    .scene(StorageSceneEnum.PRIVATE_MEDIA)
                    .bizTag(mediaId)
                    .expectedSize(request.getExpectedSize())
                    .isNeedCallback(true)
                    .build()).getData();
        } catch (Exception e) {
            log.warn("media upload init failed. mediaId={} dependency=storageService", mediaId, e);
            throw new ServiceException(MediaError.MEDIA_UPLOAD_URL_APPLY_FAILED);
        }

        Long mediaSize = request.getExpectedSize();
        if (Boolean.TRUE.equals(uploadInitResp.getFlashUploaded())) {
            try {
                StorageRecordDTO storageRecord = remoteStorageService
                        .getFileRecord(uploadInitResp.getObjectKey()).getData();
                if (storageRecord == null || storageRecord.getSize() == null) {
                    throw new ServiceException(MediaError.MEDIA_STORAGE_STATUS_GET_FAILED);
                }
                mediaSize = storageRecord.getSize();
            } catch (Exception e) {
                log.warn("media flash upload record lookup failed. mediaId={} objectKey={}",
                        mediaId, uploadInitResp.getObjectKey(), e);
                throw new ServiceException(MediaError.MEDIA_STORAGE_STATUS_GET_FAILED);
            }
        }

        MediaEntity entity = MediaEntity.builder()
                .mediaId(mediaId)
                .uploadMeta(MediaUploadMeta.builder()
                        .resourceType(resourceType)
                        .originalFilename(request.getFilename())
                        .sourceExtension(extension)
                        .size(mediaSize)
                        .uploaderId(uploaderId)
                        .uploaderGroupRoles(uploaderGroupRoles)
                        .mountTargetTagId(request.getMountTargetTagId())
                        .build())
                .mediaStatus(new MediaStatus(Boolean.TRUE.equals(uploadInitResp.getFlashUploaded())
                        ? MediaStatusEnum.UPLOADED : MediaStatusEnum.UPLOADING))
                .sourceObjectKey(uploadInitResp.getObjectKey())
                .build();
        mediaEntityRepository.save(entity);

        log.info("media upload initialized. mediaId={} objectKey={} flashUploaded={}",
                mediaId, uploadInitResp.getObjectKey(), uploadInitResp.getFlashUploaded());

        if (Boolean.TRUE.equals(uploadInitResp.getFlashUploaded())) {
            eventPublisher.publishProcessTask(MediaProcessTaskMessage.builder()
                    .mediaId(mediaId)
                    .sourceObjectKey(uploadInitResp.getObjectKey())
                    .resourceType(resourceType)
                    .extension(extension)
                    .build());
        }

        MediaUploadInitResponse response = BeanUtil.copyProperties(uploadInitResp, MediaUploadInitResponse.class);
        response.setMediaId(mediaId);
        return response;
    }

    @Override
    public List<MediaBase> listPendingMedia(Long uploaderId) {
        List<MediaEntity> entities = mediaEntityRepository.findByUploaderIdAndStatusIn(
                uploaderId,
                List.of(MediaStatusEnum.UPLOADING,
                        MediaStatusEnum.UPLOADED,
                        MediaStatusEnum.REGISTERING_RES,
                        MediaStatusEnum.TRANSFER_TIMEOUT,
                        MediaStatusEnum.REGISTERING_RES_TIMEOUT,
                        MediaStatusEnum.PROCESSING,
                        MediaStatusEnum.FAILED)
        );
        return new ArrayList<>(entities);
    }

    @Override
    public Optional<MediaStatus> getMediaStatus(String mediaId){
        return mediaEntityRepository.findById(mediaId).map(MediaEntity::getMediaStatus);
    }

    @Override
    public MediaStatus refreshMediaStatus(String mediaId) {
        MediaEntity entity = mediaEntityRepository.findById(mediaId)
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
            throw new ServiceException(MediaError.MEDIA_STORAGE_STATUS_GET_FAILED);
        }

        if (storageRecord != null) {
            entity.getUploadMeta().setSize(storageRecord.getSize() != null ? storageRecord.getSize() : entity.getUploadMeta().getSize());
            mediaEntityRepository.save(entity);
            mediaEntityRepository.updateStatusById(mediaId, new MediaStatus(MediaStatusEnum.UPLOADED));
            eventPublisher.publishProcessTask(MediaProcessTaskMessage.builder()
                    .mediaId(entity.getMediaId())
                    .sourceObjectKey(storageRecord.getObjectKey())
                    .resourceType(entity.getUploadMeta().getResourceType())
                    .extension(entity.getUploadMeta().getSourceExtension())
                    .build());
            entity.setMediaStatus(new MediaStatus(MediaStatusEnum.UPLOADED));
        }
        return entity.getMediaStatus();
    }

    @Override
    public void retryMediaProcess(String mediaId) {
        MediaEntity entity = mediaEntityRepository.findById(mediaId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        MediaStatusEnum status = entity.getMediaStatus() != null ? entity.getMediaStatus().getStatus() : null;
        if (status == MediaStatusEnum.FAILED) {
            mediaEntityRepository.updateStatusById(mediaId, new MediaStatus(MediaStatusEnum.UPLOADED));
            eventPublisher.publishProcessTask(MediaProcessTaskMessage.builder()
                    .mediaId(entity.getMediaId())
                    .sourceObjectKey(entity.getSourceObjectKey())
                    .resourceType(entity.getUploadMeta().getResourceType())
                    .extension(entity.getUploadMeta().getSourceExtension())
                    .build());
        } else if (status == MediaStatusEnum.REGISTERING_RES_TIMEOUT) {
            finalizeToReady(mediaId);
        } else {
            throw new ServiceException(MediaError.CANNOT_RETRY_MEDIA_PROCESS_IN_CURRENT_STATE);
        }
        log.info("media retry event dispatched. mediaId={} resourceId={} status={}",
                mediaId, entity.getResourceId(), status);
    }

    @Override
    public void cancelMediaProcess(String mediaId) {
        MediaEntity entity = mediaEntityRepository.findById(mediaId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        MediaStatusEnum status = entity.getMediaStatus() != null ? entity.getMediaStatus().getStatus() : null;
        if (status == MediaStatusEnum.READY) {
            throw new ServiceException(MediaError.CANNOT_CANCEL_READY_MEDIA_PROCESS);
        }
        if (status == MediaStatusEnum.PROCESSING) {
            throw new ServiceException(MediaError.CANNOT_CANCEL_MEDIA_PROCESS_IN_CURRENT_STATE);
        }

        // NOTE：在PROCESSING或READY时不能终止
        // 但由于PROCESSING可能意外失败而未能进入READY，仍需清理所有PROCESSING可能产生的资产
        deletedMediaEntity(List.of(entity));
        log.info("media process cancelled. mediaId={}", mediaId);
    }

    @Override
    public void assertMediaUploader(String mediaId, Long uploaderId) {
        MediaEntity entity = mediaEntityRepository.findById(mediaId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        if (!uploaderId.equals(entity.getUploadMeta().getUploaderId())) {
            throw new ServiceException(MediaError.MEDIA_PERMISSION_DENIED);
        }
    }

    @Override
    public MediaInfoEntity getMediaInfo(String resourceId) {
        return mediaInfoRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
    }

    @Override
    public MediaEntity getMedia(String resourceId) {
        return mediaEntityRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
    }

    @Override
    public String getOriginalDownloadUrl(String resourceId) {
        MediaEntity entity = mediaEntityRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        if (entity.getMediaStatus() == null || entity.getMediaStatus().getStatus() != MediaStatusEnum.READY) {
            throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
        }
        String filename = StrUtil.isNotBlank(entity.getUploadMeta().getOriginalFilename())
                ? entity.getUploadMeta().getOriginalFilename().trim() : "media";
        String extension = StrUtil.trim(entity.getUploadMeta().getSourceExtension());
        if (StrUtil.isNotBlank(extension)
                && !filename.toLowerCase(Locale.ROOT).endsWith("." + extension.toLowerCase(Locale.ROOT))) {
            filename = filename + "." + extension;
        }
        String contentDisposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build().toString();
        return remoteStorageService.getDownloadUrl(entity.getSourceObjectKey(), null, contentDisposition).getData();
    }

    @Override
    public void deleteMedia(List<String> resourceIds) {
        List<MediaEntity> entities = mediaEntityRepository.findByResourceIdIn(resourceIds);
        deletedMediaEntity(entities);
        mediaInfoRepository.deleteByResourceIdIn(resourceIds);
        log.info("media resources deleted. count={} resourceIds={}", resourceIds.size(), summarizeIds(resourceIds));
    }

    private void deletedMediaEntity(Iterable<MediaEntity> entities) {
        List<String> mediaIds = new ArrayList<>();
        List<String> allObjectKeys = new ArrayList<>();

        for (MediaEntity entity : entities) {
            mediaIds.add(entity.getMediaId());
            allObjectKeys.addAll(entity.collectObjectKeys());
        }

        // 发送 OSS 文件删除消息
        if (!allObjectKeys.isEmpty()) {
            eventPublisher.publishFileDeleteEvent(allObjectKeys);
        }

        // 一次操作删除所有关联记录
        mediaMetaRepository.deleteAllById(mediaIds);
        mediaEntityRepository.deleteAllById(mediaIds);

        log.info("document versions deleted. count={} mediaIds={}", mediaIds.size(), summarizeIds(mediaIds));
    }

    @Override
    public void updateStatus(String documentId, MediaStatus status) {
        MediaEntity mediaEntity = mediaEntityRepository.findById(documentId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        MediaStatusEnum from = mediaEntity.getMediaStatus() != null ? mediaEntity.getMediaStatus().getStatus() : null;
        mediaEntityRepository.updateStatusById(documentId, status);
        log.info("document status changed. documentId={} resourceId={} from={} to={}",
                documentId, mediaEntity.getResourceId(), from, status.getStatus());
    }

    public void saveMediaProcessResult(String mediaId, String previewObjectKey,
                                       List<MediaEntity.MediaHlsRenditionObjectKeys> hlsObjectKeys,
                                       MediaMetaEntity meta){
        MediaEntity entity = mediaEntityRepository.findById(mediaId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        mediaEntityRepository.updatePreviewObjectKeyById(mediaId, previewObjectKey, hlsObjectKeys);
        meta.setMediaId(mediaId);
        mediaMetaRepository.save(meta);
    }

    @Override
    public void finalizeToReady(String mediaId) {
        // finalize 可被正常处理和注册补偿重试共同调用，因此保持幂等
        MediaEntity entity = mediaEntityRepository.findById(mediaId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        MediaStatusEnum currentStatus = entity.getMediaStatus() != null ? entity.getMediaStatus().getStatus() : null;
        if (currentStatus == MediaStatusEnum.READY) {
            return;
        }
        // 进入资源注册阶段后，如果远程资源服务失败，会留下 REGISTERING_RES_TIMEOUT 供重试
        updateStatus(mediaId, new MediaStatus(MediaStatusEnum.REGISTERING_RES));
        String resourceId = entity.getResourceId();
        if (StrUtil.isBlank(resourceId)) {
            try {
                // 只有基础产物确定后才注册 resource，避免资源服务暴露未完成媒体
                String preview = null;
                // 资源服务的 preview 存 objectKey：图片使用预览图，视频使用封面，音频不提供预览图
                if (entity.getUploadMeta().getResourceType() == ResourceType.IMAGE
                        || entity.getUploadMeta().getResourceType() == ResourceType.VIDEO) {
                    preview = entity.getPreviewObjectKey();
                }
                resourceId = remoteResourceService.createResource(ResourceCreateReqDTO.builder()
                        .resourceName(entity.getUploadMeta().getOriginalFilename())
                        .resourceType(entity.getUploadMeta().getResourceType())
                        .ownerId(String.valueOf(entity.getUploadMeta().getUploaderId()))
                        .ownerGroupRoles(entity.getUploadMeta().getUploaderGroupRoles())
                        .mountTargetTagId(entity.getUploadMeta().getMountTargetTagId())
                        .preview(preview)
                        .size(entity.getUploadMeta().getSize())
                        .build()).getData();
            } catch (Exception e) {
                updateStatus(mediaId, new MediaStatus(MediaStatusEnum.REGISTERING_RES_TIMEOUT));
                throw new ServiceException(MediaError.MEDIA_REGISTER_RESOURCE_FAILED);
            }
            // 对齐 document：首次上传注册资源后新建资源级信息，再回写 resourceId
            mediaInfoRepository.save(MediaInfoEntity.builder()
                    .resourceId(resourceId)
                    .ownerId(entity.getUploadMeta().getUploaderId())
                    .build());
            mediaEntityRepository.updateResourceIdById(mediaId, resourceId);
        }

        // 资源 ID 存在后才能将媒体标记为 READY
        updateStatus(mediaId, new MediaStatus(MediaStatusEnum.READY));
        log.info("media ready finalized. mediaId={} resourceId={}", mediaId, resourceId);
    }
}
