package com.oriole.wisepen.media.api.domain.dto.res;

import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Data;

/**
 * 媒体播放响应，按 resourceType 提供图片、音频或视频播放地址。
 */
@Data
@Builder
public class MediaPlaybackResponse {

    private String resourceId;

    private String mediaId;

    private ResourceType resourceType;

    /** 图片或视频封面图 URL */
    private String coverUrl;

    /** 视频源 HLS manifest URL */
    private String manifestUrl;

    /** 图片或音频源文件播放 URL */
    private String playbackUrl;

    private Long durationMs;

    private Integer width;

    private Integer height;
}