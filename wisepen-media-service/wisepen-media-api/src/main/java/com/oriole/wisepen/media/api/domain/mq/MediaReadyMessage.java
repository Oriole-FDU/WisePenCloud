package com.oriole.wisepen.media.api.domain.mq;

import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 媒体处理就绪事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaReadyMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String resourceId;

    private String mediaId;

    private ResourceType resourceType;
}
