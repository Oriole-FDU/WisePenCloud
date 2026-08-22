package com.oriole.wisepen.media.service;

import com.oriole.wisepen.media.api.domain.dto.res.MediaPlaybackSessionResponse;

public interface IMediaWatermarkPlaybackService {

    MediaPlaybackSessionResponse createPlaybackSession(String resourceId, Long viewerId);

    MediaPlaybackSessionResponse getPlaybackSession(String sessionId, Long viewerId);

    String getPlaybackManifest(String sessionId, Long viewerId);
}
