package com.oriole.wisepen.media.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisCacheManager {

    private static final String REDIS_PLAYBACK_MANIFEST_PREFIX = "wisepen:media:playback:manifest:";

    private final StringRedisTemplate stringRedisTemplate;

    public String getPlaybackManifest(String resourceId, String playlistName) {
        return stringRedisTemplate.opsForValue().get(REDIS_PLAYBACK_MANIFEST_PREFIX + resourceId + ":" + playlistName);
    }

    public void setPlaybackManifest(String resourceId, String playlistName, String manifest, long ttlSeconds) {
        if (ttlSeconds <= 0 || manifest == null || manifest.isBlank()) {
            return;
        }
        stringRedisTemplate.opsForValue().set(REDIS_PLAYBACK_MANIFEST_PREFIX + resourceId + ":" + playlistName, manifest, ttlSeconds, TimeUnit.SECONDS);
    }

}
