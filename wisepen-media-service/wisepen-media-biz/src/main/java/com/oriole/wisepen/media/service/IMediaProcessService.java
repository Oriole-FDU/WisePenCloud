package com.oriole.wisepen.media.service;

import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.domain.mq.MediaProcessTaskMessage;

public interface IMediaProcessService {

    void processMedia(MediaProcessTaskMessage message);

    void updateStatus(String mediaId, MediaStatus status);

    void prepareProcessRetry(String mediaId);

    void markProcessFailed(String mediaId, String errorMessage);

    void finalizeToReady(String mediaId);
}
