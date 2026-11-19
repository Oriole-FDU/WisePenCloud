package com.oriole.wisepen.media.api.domain.dto.res;

import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MediaInfoResponse {

    private String mediaId;

    private String resourceId;

    private ResourceType resourceType;

    private String originalFilename;

    private String sourceExtension;

    private Long durationMs;

    private Integer width;

    private Integer height;

    private Long size;

    private MediaStatus mediaStatus;

    private LocalDateTime updateTime;
}