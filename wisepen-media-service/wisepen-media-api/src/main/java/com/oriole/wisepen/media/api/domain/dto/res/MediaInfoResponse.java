package com.oriole.wisepen.media.api.domain.dto.res;

import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 媒体资源处理信息
 */
@Data
public class MediaInfoResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String mediaId;

    private String resourceId;

    private ResourceItemResponse resourceInfo;

    private ResourceType resourceType;

    private String originalFilename;

    private String sourceExtension;

    private Long durationMs;

    private Integer width;

    private Integer height;

    private Long size;

    private MediaStatus mediaStatus;
}
