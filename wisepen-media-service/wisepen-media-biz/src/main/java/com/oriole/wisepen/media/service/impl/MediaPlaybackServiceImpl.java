package com.oriole.wisepen.media.service.impl;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.media.api.constant.MediaConstants;
import com.oriole.wisepen.media.api.domain.dto.res.MediaPlaybackResponse;
import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import com.oriole.wisepen.media.domain.entity.MediaEntity;
import com.oriole.wisepen.media.domain.entity.MediaMetaEntity;
import com.oriole.wisepen.media.exception.MediaError;
import com.oriole.wisepen.media.repository.MediaEntityRepository;
import com.oriole.wisepen.media.repository.MediaMetaRepository;
import com.oriole.wisepen.media.service.IMediaPlaybackService;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 媒体播放服务
 */
@Service
@RequiredArgsConstructor
public class MediaPlaybackServiceImpl implements IMediaPlaybackService {

    private final MediaEntityRepository mediaEntityRepository;
    private final MediaMetaRepository mediaMetaRepository;
    private final RemoteStorageService remoteStorageService;
    private final MediaHlsManifestService hlsManifestService;

    @Override
    public MediaPlaybackResponse getPlayback(String resourceId) {
        MediaEntity entity = mediaEntityRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        if (entity.getMediaStatus() == null || entity.getMediaStatus().getStatus() != MediaStatusEnum.READY) {
            throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
        }
        MediaMetaEntity meta = mediaMetaRepository.findById(entity.getMediaId()).orElse(null);
        if (meta == null) throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);

        MediaPlaybackResponse.MediaPlaybackResponseBuilder builder = MediaPlaybackResponse.builder()
                .resourceId(resourceId)
                .mediaId(entity.getMediaId())
                .resourceType(entity.getUploadMeta().getResourceType());

        // 图片
        if (entity.getUploadMeta().getResourceType() == ResourceType.IMAGE) {
            if (StrUtil.isBlank(entity.getPreviewObjectKey())) {
                throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
            }
            return builder
                    .width(meta.getWidth())
                    .height(meta.getHeight())
                    // coverUrl 为低清封面
                    .coverUrl(remoteStorageService.getDownloadUrl(entity.getPreviewObjectKey(), null, null).getData())
                    // playbackUrl 为原图
                    .playbackUrl(remoteStorageService.getDownloadUrl(entity.getSourceObjectKey(), null, null).getData())
                    .build();
        }
        // 视频
        if (entity.getUploadMeta().getResourceType() == ResourceType.VIDEO) {
            if (meta.getHls() == null) throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
            return builder
                    .durationMs(meta.getDurationMs())
                    .width(meta.getWidth())
                    .height(meta.getHeight())
                    // coverUrl 为封面
                    .coverUrl(StrUtil.isNotBlank(entity.getPreviewObjectKey())
                            ? remoteStorageService.getDownloadUrl(entity.getPreviewObjectKey(), null, null).getData()
                            : null)
                    // manifestUrl 为 HLS 获取接口，分片在拉取子清单时按需签名
                    .manifestUrl("/media/getVideoPlaybackManifest?resourceId=" + resourceId)
                    .build();
        }
        // 音频
        if (entity.getUploadMeta().getResourceType() == ResourceType.AUDIO) {
            return builder
                    .durationMs(meta.getDurationMs())
                    // playbackUrl 为音频
                    .playbackUrl(remoteStorageService.getDownloadUrl(entity.getSourceObjectKey(), null, null).getData())
                    .build();
        }
        throw new ServiceException(MediaError.CANNOT_SUPPORT_FILE_TYPE);
    }

    @Override
    public String getVideoPlaybackManifest(String resourceId, String manifestPath) {
        MediaEntity entity = mediaEntityRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        // 仅视频可用
        if (entity.getUploadMeta().getResourceType() != ResourceType.VIDEO) {
            throw new ServiceException(MediaError.MEDIA_PLAYBACK_FAILED);
        }

        if (entity.getMediaStatus() == null || entity.getMediaStatus().getStatus() != MediaStatusEnum.READY) {
            throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
        }

        if (manifestPath == null || !manifestPath.matches("[A-Za-z0-9_-]+\\.m3u8")) {
            throw new ServiceException(MediaError.MEDIA_PLAYBACK_FAILED);
        }

        MediaMetaEntity meta = mediaMetaRepository.findById(entity.getMediaId())
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_PLAYBACK_FAILED));

        // 只放行主清单或本媒体已登记的档位清单
        boolean registered = MediaConstants.MASTER_PLAYLIST_NAME.equals(manifestPath)
                || meta.getHls() != null && meta.getHls().stream()
                        .anyMatch(rendition -> manifestPath.equals(rendition.getPlaylistName()));
        if (!registered) {
            throw new ServiceException(MediaError.MEDIA_PLAYBACK_FAILED);
        }
        return hlsManifestService.getSignedManifest(resourceId, entity, meta, manifestPath);
    }
}
