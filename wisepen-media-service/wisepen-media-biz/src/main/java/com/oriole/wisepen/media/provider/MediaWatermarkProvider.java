package com.oriole.wisepen.media.provider;

import com.oriole.wisepen.media.domain.MediaPlaybackGrant;
import com.oriole.wisepen.media.domain.entity.MediaInfoEntity;
import com.oriole.wisepen.media.domain.entity.MediaWatermarkSessionEntity;

/**
 * 媒体取证水印 provider seam。
 */
public interface MediaWatermarkProvider {

    MediaPlaybackGrant createPlaybackGrant(MediaInfoEntity mediaInfo,
                                           MediaWatermarkSessionEntity session);
}
