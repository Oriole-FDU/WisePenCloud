package com.oriole.wisepen.media.service;

import com.oriole.wisepen.media.api.domain.dto.res.MediaPlaybackResponse;

public interface IMediaPlaybackService {

    MediaPlaybackResponse getPlayback(String resourceId);

    String getVideoPlaybackManifest(String resourceId, String manifestPath);
}
