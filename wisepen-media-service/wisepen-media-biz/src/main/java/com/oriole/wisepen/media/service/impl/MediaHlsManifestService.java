package com.oriole.wisepen.media.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.exception.ServiceException;
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
import java.util.HashMap;
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
            .build();

    private final RemoteStorageService remoteStorageService;
    private final MediaProperties mediaProperties;
    private final RedisCacheManager redisCacheManager;

    public String getSignedManifest(String manifestObjectKey, String cacheKey, long ttlSeconds) {
        try {
            // 读缓存
            String cachedManifest = null;
            try {
                cachedManifest = redisCacheManager.getPlaybackManifest(cacheKey);
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
                        cacheKey,
                        lockToken,
                        mediaProperties.getPlaybackManifestBuildLockTtlSeconds());
            } catch (Exception e) {
                log.warn("media hls manifest cache lock failed. manifestObjectKey={}", manifestObjectKey, e);
            }

            boolean lockAcquired = Boolean.TRUE.equals(lockResult);
            try {
                // 自己拿到锁
                if (lockAcquired) {
                    // 再读一次缓存，防止刚好别人已经写入
                    try {
                        cachedManifest = redisCacheManager.getPlaybackManifest(cacheKey);
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
                    cachedManifest = waitForCachedManifest(cacheKey, manifestObjectKey);
                    if (cachedManifest != null) {
                        return cachedManifest;
                    }
                }

                String manifest = buildSignedManifest(manifestObjectKey);
                if (ttlSeconds > 0) {
                    try {
                        redisCacheManager.setPlaybackManifest(cacheKey, manifest, ttlSeconds);
                    } catch (Exception e) {
                        log.warn("media hls manifest cache write failed. manifestObjectKey={}", manifestObjectKey, e);
                    }
                }
                return manifest;
            } finally {
                if (lockAcquired) {
                    try {
                        redisCacheManager.unlockPlaybackManifestBuild(cacheKey, lockToken);
                    } catch (Exception e) {
                        log.warn("media hls manifest cache unlock failed. manifestObjectKey={}", manifestObjectKey, e);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("media hls manifest build failed. manifestObjectKey={}", manifestObjectKey, e);
            throw new ServiceException(MediaError.MEDIA_PLAYBACK_FAILED, e.getMessage());
        }
    }

    private String buildSignedManifest(String manifestObjectKey) throws Exception {
        // 下载 index.m3u8
        String manifestUrl = remoteStorageService.getDownloadUrl(manifestObjectKey, null, null).getData();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(manifestUrl)).GET().build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("媒体 HLS manifest 下载失败 StatusCode=" + response.statusCode());
        }

        String manifest = response.body();
        StringBuilder builder = new StringBuilder();
        int lastSlash = manifestObjectKey.lastIndexOf('/');
        String segmentPrefix = lastSlash >= 0 ? manifestObjectKey.substring(0, lastSlash) : "";
        Map<String, String> segmentUrls = new HashMap<>();
        for (String line : manifest.split("\\R", -1)) {
            if (StrUtil.isBlank(line) || line.startsWith("#") || line.startsWith("http://") || line.startsWith("https://")) {
                builder.append(line).append('\n');
                continue;
            }
            String cleanLine = line.replace('\\', '/').replaceAll("^/+", "");
            String segmentObjectKey = StrUtil.isBlank(segmentPrefix) ? cleanLine : segmentPrefix + "/" + cleanLine;
            String segmentUrl = segmentUrls.computeIfAbsent(segmentObjectKey,
                    key -> remoteStorageService.getDownloadUrl(key, mediaProperties.getHlsSegmentUrlTtlSeconds(), null).getData());
            builder.append(segmentUrl).append('\n');
        }
        return builder.toString();
    }

    private String waitForCachedManifest(String cacheKey, String manifestObjectKey) {
        for (int i = 0; i < MANIFEST_CACHE_WAIT_ATTEMPTS; i++) {
            try {
                Thread.sleep(MANIFEST_CACHE_WAIT_MILLIS);
                String manifest = redisCacheManager.getPlaybackManifest(cacheKey);
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
