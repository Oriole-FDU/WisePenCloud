package com.oriole.wisepen.media.api.domain.mq;

import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 媒体处理就绪事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaReadyMessage {

    private String resourceId;

    private String mediaId;

    private ResourceType resourceType;
}