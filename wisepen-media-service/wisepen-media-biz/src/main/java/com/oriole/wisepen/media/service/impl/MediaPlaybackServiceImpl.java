package com.oriole.wisepen.media.service.impl;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.media.api.domain.dto.res.MediaPlaybackResponse;
import com.oriole.wisepen.media.api.enums.MediaDeliveryMode;
import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import com.oriole.wisepen.media.config.MediaProperties;
import com.oriole.wisepen.media.domain.entity.MediaInfoEntity;
import com.oriole.wisepen.media.exception.MediaError;
import com.oriole.wisepen.media.repository.MediaInfoRepository;
import com.oriole.wisepen.media.service.IMediaPlaybackService;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 无水印媒体播放服务，按媒体类型返回封面图、源文件或源 HLS 播放地址
 */
@Service
@RequiredArgsConstructor
public class MediaPlaybackServiceImpl implements IMediaPlaybackService {

    private final MediaInfoRepository mediaInfoRepository;
    private final RemoteStorageService remoteStorageService;
    private final MediaHlsManifestService hlsManifestService;
    private final MediaProperties mediaProperties;

    @Override
    public MediaPlaybackResponse getPlayback(String resourceId) {
        MediaInfoEntity mediaInfo = mediaInfoRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        if (mediaInfo.getMediaStatus() == null || mediaInfo.getMediaStatus().getStatus() != MediaStatusEnum.READY) {
            throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
        }
        MediaPlaybackResponse.MediaPlaybackResponseBuilder builder = MediaPlaybackResponse.builder()
                .resourceId(resourceId)
                .mediaId(mediaInfo.getMediaId())
                .resourceType(mediaInfo.getResourceType())
                .durationMs(mediaInfo.getDurationMs())
                .width(mediaInfo.getWidth())
                .height(mediaInfo.getHeight());

        if (mediaInfo.getResourceType() == ResourceType.IMAGE) {
            if (StrUtil.isBlank(mediaInfo.getPreviewObjectKey())) {
                throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
            }
            return builder
                    .deliveryMode(MediaDeliveryMode.IMAGE_SOURCE)
                    .coverUrl(remoteStorageService.getDownloadUrl(mediaInfo.getPreviewObjectKey(), null, null).getData())
                    .playbackUrl(remoteStorageService.getDownloadUrl(mediaInfo.getSourceObjectKey(), null, null).getData())
                    .build();
        }
        if (mediaInfo.getResourceType() == ResourceType.VIDEO) {
            if (StrUtil.isBlank(mediaInfo.getSourceHlsPrefix())) {
                throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
            }
            return builder
                    .deliveryMode(MediaDeliveryMode.VIDEO_SOURCE_HLS)
                    .coverUrl(StrUtil.isNotBlank(mediaInfo.getPreviewObjectKey())
                            ? remoteStorageService.getDownloadUrl(mediaInfo.getPreviewObjectKey(), null, null).getData()
                            : null)
                    .manifestUrl("/media/getPlaybackManifest?resourceId=" + resourceId)
                    .build();
        }
        if (mediaInfo.getResourceType() == ResourceType.AUDIO) {
            return builder
                    .deliveryMode(MediaDeliveryMode.AUDIO_SOURCE)
                    .playbackUrl(remoteStorageService.getDownloadUrl(mediaInfo.getSourceObjectKey(), null, null).getData())
                    .build();
        }
        throw new ServiceException(MediaError.CANNOT_SUPPORT_FILE_TYPE);
    }

    @Override
    public String getPlaybackManifest(String resourceId) {
        MediaInfoEntity mediaInfo = mediaInfoRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        if (mediaInfo.getMediaStatus() == null || mediaInfo.getMediaStatus().getStatus() != MediaStatusEnum.READY) {
            throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
        }
        if (mediaInfo.getResourceType() != ResourceType.VIDEO || StrUtil.isBlank(mediaInfo.getSourceHlsPrefix())) {
            throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
        }
        String manifestObjectKey = mediaInfo.getSourceHlsPrefix() + "/index.m3u8";
        return hlsManifestService.getSignedManifest(manifestObjectKey,
                "source:" + mediaInfo.getMediaId(),
                mediaProperties.getHlsSegmentUrlTtlSeconds()
                        - mediaProperties.getPlaybackManifestCacheSafetySeconds());
    }

}
