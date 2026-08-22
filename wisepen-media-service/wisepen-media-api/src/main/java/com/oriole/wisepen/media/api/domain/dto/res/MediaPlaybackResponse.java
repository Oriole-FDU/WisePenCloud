package com.oriole.wisepen.media.api.domain.dto.res;

import com.oriole.wisepen.media.api.enums.MediaDeliveryMode;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 媒体无水印播放响应
 */
@Data
@Builder
public class MediaPlaybackResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String resourceId;

    private String mediaId;

    private ResourceType resourceType;

    /** 播放交付模式 */
    private MediaDeliveryMode deliveryMode;

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
