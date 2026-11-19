package com.oriole.wisepen.media.api.domain.mq;

import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 媒体处理任务消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaProcessTaskMessage {

    private String mediaId;

    private String sourceObjectKey;

    private ResourceType resourceType;

    private String extension;
}