package com.oriole.wisepen.media.service.impl;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.domain.dto.DownloadUrlsReqDTO;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.media.api.constant.MediaConstants;
import com.oriole.wisepen.media.cache.RedisCacheManager;
import com.oriole.wisepen.media.config.MediaProperties;
import com.oriole.wisepen.media.domain.entity.MediaEntity;
import com.oriole.wisepen.media.domain.entity.MediaEntity.MediaHlsRenditionObjectKeys;
import com.oriole.wisepen.media.domain.entity.MediaMetaEntity.MediaHlsRendition;
import com.oriole.wisepen.media.domain.entity.MediaMetaEntity;
import com.oriole.wisepen.media.exception.MediaError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 媒体 HLS 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaHlsManifestService {

    private final RemoteStorageService remoteStorageService;
    private final MediaProperties mediaProperties;
    private final RedisCacheManager redisCacheManager;

    public String getSignedManifest(String resourceId, MediaEntity entity, MediaMetaEntity meta, String playlistName) {
        try {
            // 先读缓存，命中直接返回
            String cachedManifest = null;
            try {
                cachedManifest = redisCacheManager.getPlaybackManifest(resourceId, playlistName);
            } catch (Exception e) {
                log.warn("media hls manifest cache read failed. resourceId={} playlistName={}", resourceId, playlistName, e);
            }
            if (StrUtil.isNotBlank(cachedManifest)) return cachedManifest;

            // 缓存未命中，构建清单

            long playbackSeconds = meta.getDurationMs() == null ? 0 : (long) Math.ceil(meta.getDurationMs() / 1000.0);
            // 确保从签名那一刻起，直到最晚拿到清单的观众 + 完整播放结束，分片 URL 都还有效
            long segmentUrlTtlSeconds = mediaProperties.getPlayback().getHlsSegmentUrlTtlSeconds() + playbackSeconds;

            String manifest;
            if (MediaConstants.MASTER_PLAYLIST_NAME.equals(playlistName)) {
                manifest = renderMaster(resourceId, meta.getHls());
            } else {
                // 分片 Meta
                MediaHlsRendition rendition = meta.getHls().stream()
                        .filter(r -> playlistName.equals(r.getPlaylistName()))
                        .findFirst()
                        .orElseThrow(() -> new ServiceException(MediaError.MEDIA_PLAYBACK_FAILED));
                // 分片 Object
                MediaHlsRenditionObjectKeys keys = entity.getHlsObjectKeys().stream()
                        .filter(k -> playlistName.equals(k.getPlaylistName()))
                        .findFirst()
                        .orElseThrow(() -> new ServiceException(MediaError.MEDIA_PLAYBACK_FAILED));
                manifest = renderMediaPlaylist(rendition, keys, segmentUrlTtlSeconds);
            }

            long ttlSeconds = mediaProperties.getPlayback().getHlsSegmentUrlTtlSeconds();
            if (ttlSeconds > 0) {
                try {
                    redisCacheManager.setPlaybackManifest(resourceId, playlistName, manifest, ttlSeconds);
                } catch (Exception e) {
                    log.warn("media hls manifest cache write failed. resourceId={} playlistName={}", resourceId, playlistName, e);
                }
            }
            return manifest;
        } catch (Exception e) {
            throw new ServiceException(MediaError.MEDIA_PLAYBACK_FAILED);
        }
    }

    private String renderMaster(String resourceId, List<MediaHlsRendition> renditions) {
        // 主清单格式：#EXTM3U 头 + 每个档位一条 STREAM-INF
        StringBuilder builder = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:7\n");
        for (MediaHlsRendition rendition : renditions) {
            // BANDWIDTH/RESOLUTION/CODECS 供播放器按带宽与解码能力选档
            builder.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(rendition.getBandwidth())
                    .append(",RESOLUTION=").append(rendition.getResolution())
                    .append(",CODECS=\"").append(rendition.getCodecs()).append("\"\n");
            // 把子清单地址回指到媒体服务自己的接口
            builder.append(UriComponentsBuilder.fromPath("getPlaybackManifest")
                    .queryParam("resourceId", resourceId)
                    .queryParam("manifestPath", rendition.getPlaylistName())
                    .build().encode().toUriString()).append('\n');
        }
        return builder.toString();
    }

    private String renderMediaPlaylist(MediaHlsRendition rendition, MediaHlsRenditionObjectKeys keys, long segmentUrlTtlSeconds) {
        // init 与全部分片一起批量签名
        List<String> objectKeys = new ArrayList<>();
        objectKeys.add(keys.getInitObjectKey());
        objectKeys.addAll(keys.getSegmentObjectKeys());
        Map<String, String> signedUrls = remoteStorageService.getDownloadUrls(DownloadUrlsReqDTO.builder()
                .objectKeys(objectKeys)
                .durationSeconds(segmentUrlTtlSeconds)
                .build()).getData();
        if (signedUrls == null || objectKeys.stream().anyMatch(key -> StrUtil.isBlank(signedUrls.get(key)))) {
            // 有一个分片未能获取就全部失败
            throw new ServiceException(MediaError.MEDIA_PLAYBACK_FAILED);
        }

        // TARGETDURATION 必须是整数秒且 ≥ 最大分片时长（向上取整），否则不合 HLS 规范
        int targetDuration = (int) Math.ceil(rendition.getSegmentDurations().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0D));

        StringBuilder builder = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:7\n");
        builder.append("#EXT-X-TARGETDURATION:").append(targetDuration).append('\n');
        // fMP4 需先用 EXT-X-MAP 声明 init 段，播放器下载分片前先取 init 的解码参数
        builder.append("#EXT-X-MAP:URI=\"").append(signedUrls.get(keys.getInitObjectKey())).append("\"\n");
        List<String> segmentKeys = keys.getSegmentObjectKeys();
        List<Double> durations = rendition.getSegmentDurations();
        for (int i = 0; i < segmentKeys.size(); i++) {
            // 每个分片：EXTINF 声明时长，紧跟一行分片的 URL
            builder.append("#EXTINF:").append(String.format(Locale.ROOT, "%.6f", durations.get(i))).append(",\n");
            builder.append(signedUrls.get(segmentKeys.get(i))).append('\n');
        }
        // VOD 清单以 ENDLIST 收尾，播放器据此知道清单已完整、无需刷新
        builder.append("#EXT-X-ENDLIST\n");
        return builder.toString();
    }
}
