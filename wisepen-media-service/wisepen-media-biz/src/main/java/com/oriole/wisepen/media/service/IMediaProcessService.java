package com.oriole.wisepen.media.service;

import com.oriole.wisepen.media.api.domain.mq.MediaProcessTaskMessage;

public interface IMediaProcessService {

    void processMedia(MediaProcessTaskMessage message);

}
