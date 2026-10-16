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
    private static final String REDIS_PLAYBACK_MANIFEST_LOCK_PREFIX = "wisepen:media:playback:manifest:lock:";
    private static final DefaultRedisScript<Long> UNLOCK_PLAYBACK_MANIFEST_BUILD_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public String getPlaybackManifest(String sessionId) {
        return stringRedisTemplate.opsForValue().get(REDIS_PLAYBACK_MANIFEST_PREFIX + sessionId);
    }

    public void setPlaybackManifest(String sessionId, String manifest, long ttlSeconds) {
        if (ttlSeconds <= 0 || manifest == null || manifest.isBlank()) {
            return;
        }
        stringRedisTemplate.opsForValue().set(REDIS_PLAYBACK_MANIFEST_PREFIX + sessionId, manifest, ttlSeconds, TimeUnit.SECONDS);
    }

    public Boolean tryLockPlaybackManifestBuild(String sessionId, String lockToken, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return Boolean.FALSE;
        }
        return stringRedisTemplate.opsForValue().setIfAbsent(REDIS_PLAYBACK_MANIFEST_LOCK_PREFIX + sessionId, lockToken, ttlSeconds, TimeUnit.SECONDS);
    }

    public void unlockPlaybackManifestBuild(String sessionId, String lockToken) {
        stringRedisTemplate.execute(UNLOCK_PLAYBACK_MANIFEST_BUILD_SCRIPT,
                List.of(REDIS_PLAYBACK_MANIFEST_LOCK_PREFIX + sessionId),
                lockToken);
    }

}
