package com.oriole.wisepen.media.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.domain.dto.DownloadUrlsReqDTO;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.media.cache.RedisCacheManager;
import com.oriole.wisepen.media.config.MediaProperties;
import com.oriole.wisepen.media.exception.MediaError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HLS manifest 交付服务，负责下载 OSS 中的 manifest 并把分片地址改写为短时签名 URL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaHlsManifestService {

    private static final int MANIFEST_CACHE_WAIT_ATTEMPTS = 10;
    private static final long MANIFEST_CACHE_WAIT_MILLIS = 100L;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final RemoteStorageService remoteStorageService;
    private final MediaProperties mediaProperties;
    private final RedisCacheManager redisCacheManager;

    public String getSignedManifest(String mediaId, String manifestObjectKey, Long durationMs) {
        try {
            // 读缓存
            String cachedManifest = null;
            try {
                cachedManifest = redisCacheManager.getPlaybackManifest(mediaId);
            } catch (Exception e) {
                log.warn("media hls manifest cache read failed. manifestObjectKey={}", manifestObjectKey, e);
            }
            if (StrUtil.isNotBlank(cachedManifest)) {
                return cachedManifest;
            }

            // 没命中时尝试拿 Redis 构建锁
            String lockToken = IdUtil.fastSimpleUUID();
            Boolean lockResult = null;
            try {
                lockResult = redisCacheManager.tryLockPlaybackManifestBuild(
                        mediaId,
                        lockToken,
                        mediaProperties.getPlayback().getManifestBuildLockTtlSeconds());
            } catch (Exception e) {
                log.warn("media hls manifest cache lock failed. manifestObjectKey={}", manifestObjectKey, e);
            }

            boolean lockAcquired = Boolean.TRUE.equals(lockResult);
            try {
                // 自己拿到锁
                if (lockAcquired) {
                    // 再读一次缓存，防止刚好别人已经写入
                    try {
                        cachedManifest = redisCacheManager.getPlaybackManifest(mediaId);
                    } catch (Exception e) {
                        log.warn("media hls manifest cache read failed. manifestObjectKey={}", manifestObjectKey, e);
                    }
                    if (StrUtil.isNotBlank(cachedManifest)) {
                        return cachedManifest;
                    }
                }
                // 别人拿到锁
                else if (Boolean.FALSE.equals(lockResult)) {
                    // 等对方写缓存
                    cachedManifest = waitForCachedManifest(mediaId, manifestObjectKey);
                    if (cachedManifest != null) {
                        return cachedManifest;
                    }
                }

                // 缓存命中后的新播放仍需完整视频时长；构建耗时也计入签名寿命。
                long buildStarted = System.nanoTime();
                long playbackSeconds = durationMs == null ? 0 : (long) Math.ceil(durationMs / 1000.0);
                long segmentUrlTtlSeconds = mediaProperties.getPlayback().getHlsSegmentUrlTtlSeconds() + playbackSeconds;
                String manifest = buildSignedManifest(manifestObjectKey, segmentUrlTtlSeconds);
                long buildSeconds = (long) Math.ceil((System.nanoTime() - buildStarted) / 1_000_000_000.0);
                long ttlSeconds = mediaProperties.getPlayback().getHlsSegmentUrlTtlSeconds()
                        - mediaProperties.getPlayback().getManifestCacheSafetySeconds() - buildSeconds;
                if (ttlSeconds > 0) {
                    try {
                        redisCacheManager.setPlaybackManifest(mediaId, manifest, ttlSeconds);
                    } catch (Exception e) {
                        log.warn("media hls manifest cache write failed. manifestObjectKey={}", manifestObjectKey, e);
                    }
                }
                return manifest;
            } finally {
                if (lockAcquired) {
                    try {
                        redisCacheManager.unlockPlaybackManifestBuild(mediaId, lockToken);
                    } catch (Exception e) {
                        log.warn("media hls manifest cache unlock failed. manifestObjectKey={}", manifestObjectKey, e);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("media hls manifest build failed. manifestObjectKey={}", manifestObjectKey, e);
            throw new ServiceException(MediaError.MEDIA_PLAYBACK_FAILED);
        }
    }

    private String buildSignedManifest(String manifestObjectKey, long segmentUrlTtlSeconds) throws Exception {
        // 下载 index.m3u8
        String manifestUrl = remoteStorageService.getDownloadUrl(manifestObjectKey, null, null).getData();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(manifestUrl))
                .timeout(Duration.ofMillis(mediaProperties.getProcessingRuntime().getHttpRequestTimeoutMs()))
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("媒体 HLS manifest 下载失败 StatusCode=" + response.statusCode());
        }

        String manifest = response.body();
        StringBuilder builder = new StringBuilder();
        int lastSlash = manifestObjectKey.lastIndexOf('/');
        String segmentPrefix = lastSlash >= 0 ? manifestObjectKey.substring(0, lastSlash) : "";
        Map<String, String> segmentUrls = new HashMap<>();
        Map<String, String> segmentKeys = new LinkedHashMap<>();
        for (String line : manifest.split("\\R", -1)) {
            if (StrUtil.isBlank(line) || line.startsWith("#") || line.startsWith("http://") || line.startsWith("https://")) {
                continue;
            }
            String cleanLine = line.replace('\\', '/').replaceAll("^/+", "");
            String segmentObjectKey = StrUtil.isBlank(segmentPrefix) ? cleanLine : segmentPrefix + "/" + cleanLine;
            segmentKeys.put(line, segmentObjectKey);
        }
        List<String> objectKeys = new ArrayList<>(segmentKeys.values().stream().distinct().toList());
        for (int start = 0; start < objectKeys.size(); start += DownloadUrlsReqDTO.MAX_BATCH_SIZE) {
            List<String> batch = objectKeys.subList(start, Math.min(start + DownloadUrlsReqDTO.MAX_BATCH_SIZE, objectKeys.size()));
            Map<String, String> batchUrls = remoteStorageService.getDownloadUrls(DownloadUrlsReqDTO.builder()
                    .objectKeys(batch)
                    .durationSeconds(segmentUrlTtlSeconds)
                    .build()).getData();
            if (batchUrls == null || batch.stream().anyMatch(key -> StrUtil.isBlank(batchUrls.get(key)))) {
                throw new ServiceException(MediaError.MEDIA_PLAYBACK_FAILED);
            }
            segmentUrls.putAll(batchUrls);
        }
        for (String line : manifest.split("\\R", -1)) {
            String objectKey = segmentKeys.get(line);
            builder.append(objectKey == null ? line : segmentUrls.get(objectKey)).append('\n');
        }
        return builder.toString();
    }

    private String waitForCachedManifest(String mediaId, String manifestObjectKey) {
        for (int i = 0; i < MANIFEST_CACHE_WAIT_ATTEMPTS; i++) {
            try {
                Thread.sleep(MANIFEST_CACHE_WAIT_MILLIS);
                String manifest = redisCacheManager.getPlaybackManifest(mediaId);
                if (StrUtil.isNotBlank(manifest)) {
                    return manifest;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.warn("media hls manifest cache wait failed. manifestObjectKey={}", manifestObjectKey, e);
                return null;
            }
        }
        return null;
    }
}
