package com.oriole.wisepen.media.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.media.api.domain.dto.res.MediaPlaybackSessionResponse;
import com.oriole.wisepen.media.api.enums.ForensicStatus;
import com.oriole.wisepen.media.api.enums.MediaDeliveryMode;
import com.oriole.wisepen.media.api.enums.MediaStatusEnum;
import com.oriole.wisepen.media.api.enums.WatermarkPurpose;
import com.oriole.wisepen.media.api.enums.WatermarkSessionStatus;
import com.oriole.wisepen.media.config.MediaProperties;
import com.oriole.wisepen.media.domain.MediaPlaybackGrant;
import com.oriole.wisepen.media.domain.entity.MediaInfoEntity;
import com.oriole.wisepen.media.domain.entity.MediaWatermarkSessionEntity;
import com.oriole.wisepen.media.exception.MediaError;
import com.oriole.wisepen.media.provider.MediaWatermarkProvider;
import com.oriole.wisepen.media.repository.MediaInfoRepository;
import com.oriole.wisepen.media.repository.MediaWatermarkSessionRepository;
import com.oriole.wisepen.media.service.IMediaWatermarkPlaybackService;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 取证水印播放服务，保留图片和视频的水印会话创建、查询和 HLS manifest 交付能力。
 */
@Service
@RequiredArgsConstructor
public class MediaWatermarkPlaybackServiceImpl implements IMediaWatermarkPlaybackService {

    private static final DateTimeFormatter WATERMARK_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final CopyOptions IGNORE_NULL_COPY_OPTIONS = CopyOptions.create().ignoreNullValue();

    private final MediaInfoRepository mediaInfoRepository;
    private final MediaWatermarkSessionRepository watermarkSessionRepository;
    private final RemoteStorageService remoteStorageService;
    private final MediaWatermarkProvider mediaWatermarkProvider;
    private final MediaProperties mediaProperties;
    private final MediaHlsManifestService hlsManifestService;

    @Override
    public MediaPlaybackSessionResponse createPlaybackSession(String resourceId, Long viewerId) {
        MediaInfoEntity mediaInfo = mediaInfoRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        if (mediaInfo.getMediaStatus() == null || mediaInfo.getMediaStatus().getStatus() != MediaStatusEnum.READY) {
            throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
        }
        if (mediaInfo.getResourceType() == ResourceType.AUDIO) {
            return MediaPlaybackSessionResponse.builder()
                    .status(WatermarkSessionStatus.READY)
                    .deliveryMode(MediaDeliveryMode.AUDIO_SOURCE)
                    .forensicStatus(ForensicStatus.UNAVAILABLE)
                    .playbackUrl(remoteStorageService.getDownloadUrl(mediaInfo.getSourceObjectKey(), null, null).getData())
                    .build();
        }

        LocalDateTime accessedAt = LocalDateTime.now();
        String sessionId = IdUtil.fastSimpleUUID();
        String wmId = IdUtil.fastSimpleUUID();
        WatermarkPurpose purpose = mediaInfo.getResourceType() == ResourceType.IMAGE
                ? WatermarkPurpose.PREVIEW : WatermarkPurpose.PLAYBACK;
        MediaDeliveryMode deliveryMode = mediaInfo.getResourceType() == ResourceType.IMAGE
                ? MediaDeliveryMode.IMAGE_PREVIEW : MediaDeliveryMode.VIDEO_JIT_HLS;

        MediaWatermarkSessionEntity session = MediaWatermarkSessionEntity.builder()
                .sessionId(sessionId)
                .wmId(wmId)
                .viewerId(viewerId)
                .resourceId(resourceId)
                .mediaId(mediaInfo.getMediaId())
                .purpose(purpose)
                .accessedAt(accessedAt)
                .expiresAt(accessedAt.plusMinutes(mediaProperties.getSessionTtlMinutes()))
                .watermarkText(viewerId + " " + accessedAt.format(WATERMARK_TIME_FORMAT) + " " + mediaProperties.getAcademicUseText())
                .deliveryMode(deliveryMode)
                .status(WatermarkSessionStatus.PREPARING)
                .forensicStatus(ForensicStatus.PREPARING)
                .build();
        watermarkSessionRepository.save(session);

        // 会话先落库再调用 provider，确保后续泄露检测可以用 wmId 反查 viewer/resource/session。
        MediaPlaybackGrant grant = mediaWatermarkProvider.createPlaybackGrant(mediaInfo, session);
        // VIEW 权限不等于允许看源文件；暗水印不可用时直接 fail closed。
        if (grant.getForensicStatus() == ForensicStatus.UNAVAILABLE) {
            BeanUtil.copyProperties(MediaWatermarkSessionEntity.builder()
                    .status(WatermarkSessionStatus.FAILED)
                    .forensicStatus(ForensicStatus.UNAVAILABLE)
                    .build(), session, IGNORE_NULL_COPY_OPTIONS);
            watermarkSessionRepository.save(session);
            throw new ServiceException(MediaError.MEDIA_FORENSIC_UNAVAILABLE);
        }
        BeanUtil.copyProperties(MediaWatermarkSessionEntity.builder()
                .status(grant.getStatus())
                .deliveryMode(grant.getDeliveryMode())
                .forensicStatus(grant.getForensicStatus())
                .previewObjectKey(grant.getPreviewObjectKey())
                .manifestObjectKey(grant.getManifestObjectKey())
                .deliveryObjectKeys(grant.getDeliveryObjectKeys())
                .build(), session, IGNORE_NULL_COPY_OPTIONS);
        watermarkSessionRepository.save(session);
        MediaPlaybackSessionResponse.MediaPlaybackSessionResponseBuilder builder = MediaPlaybackSessionResponse.builder()
                .sessionId(session.getSessionId())
                .status(session.getStatus())
                .deliveryMode(session.getDeliveryMode())
                .forensicStatus(session.getForensicStatus())
                .watermarkText(session.getWatermarkText())
                .retryAfterMs(grant.getRetryAfterMs());
        boolean ready = session.getStatus() == WatermarkSessionStatus.READY
                || session.getStatus() == WatermarkSessionStatus.FINISHED;
        if (ready && session.getDeliveryMode() == MediaDeliveryMode.IMAGE_PREVIEW
                && StrUtil.isNotBlank(session.getPreviewObjectKey())) {
            builder.previewUrl(remoteStorageService.getDownloadUrl(session.getPreviewObjectKey(), null, null).getData());
        }
        if (ready && (session.getDeliveryMode() == MediaDeliveryMode.VIDEO_JIT_HLS
                || session.getDeliveryMode() == MediaDeliveryMode.VIDEO_AB_HLS)
                && StrUtil.isNotBlank(session.getManifestObjectKey())) {
            builder.manifestUrl("/media/getWatermarkPlaybackManifest?sessionId=" + session.getSessionId());
        }
        return builder.build();
    }

    @Override
    public MediaPlaybackSessionResponse getPlaybackSession(String sessionId, Long viewerId) {
        MediaWatermarkSessionEntity session = watermarkSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_PLAYBACK_SESSION_NOT_FOUND));
        if (!viewerId.equals(session.getViewerId()) || session.getExpiresAt() == null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ServiceException(MediaError.MEDIA_PLAYBACK_SESSION_NOT_FOUND);
        }
        MediaPlaybackSessionResponse.MediaPlaybackSessionResponseBuilder builder = MediaPlaybackSessionResponse.builder()
                .sessionId(session.getSessionId())
                .status(session.getStatus())
                .deliveryMode(session.getDeliveryMode())
                .forensicStatus(session.getForensicStatus())
                .watermarkText(session.getWatermarkText())
                .retryAfterMs(null);
        boolean ready = session.getStatus() == WatermarkSessionStatus.READY
                || session.getStatus() == WatermarkSessionStatus.FINISHED;
        if (ready && session.getDeliveryMode() == MediaDeliveryMode.IMAGE_PREVIEW
                && StrUtil.isNotBlank(session.getPreviewObjectKey())) {
            builder.previewUrl(remoteStorageService.getDownloadUrl(session.getPreviewObjectKey(), null, null).getData());
        }
        if (ready && (session.getDeliveryMode() == MediaDeliveryMode.VIDEO_JIT_HLS
                || session.getDeliveryMode() == MediaDeliveryMode.VIDEO_AB_HLS)
                && StrUtil.isNotBlank(session.getManifestObjectKey())) {
            builder.manifestUrl("/media/getWatermarkPlaybackManifest?sessionId=" + session.getSessionId());
        }
        return builder.build();
    }

    @Override
    public String getPlaybackManifest(String sessionId, Long viewerId) {
        MediaWatermarkSessionEntity session = watermarkSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_PLAYBACK_SESSION_NOT_FOUND));
        if (!viewerId.equals(session.getViewerId()) || session.getExpiresAt() == null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ServiceException(MediaError.MEDIA_PLAYBACK_SESSION_NOT_FOUND);
        }
        if (session.getDeliveryMode() != MediaDeliveryMode.VIDEO_JIT_HLS && session.getDeliveryMode() != MediaDeliveryMode.VIDEO_AB_HLS) {
            throw new ServiceException(MediaError.MEDIA_PLAYBACK_SESSION_NOT_FOUND);
        }
        if (session.getStatus() != WatermarkSessionStatus.READY && session.getStatus() != WatermarkSessionStatus.FINISHED) {
            throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
        }
        MediaInfoEntity mediaInfo = mediaInfoRepository.findById(session.getMediaId())
                .orElseThrow(() -> new ServiceException(MediaError.MEDIA_NOT_FOUND));
        if (mediaInfo.getMediaStatus() == null || mediaInfo.getMediaStatus().getStatus() != MediaStatusEnum.READY) {
            throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
        }
        if (StrUtil.isBlank(session.getManifestObjectKey())) {
            throw new ServiceException(MediaError.MEDIA_PREVIEW_NOT_READY);
        }

        long sessionRemainingSeconds = Duration.between(LocalDateTime.now(), session.getExpiresAt()).getSeconds();
        long ttlSeconds = Math.min(sessionRemainingSeconds, mediaProperties.getHlsSegmentUrlTtlSeconds()
                - mediaProperties.getPlaybackManifestCacheSafetySeconds());
        return hlsManifestService.getSignedManifest(session.getManifestObjectKey(),
                "watermark:" + session.getSessionId(),
                ttlSeconds);
    }

}
