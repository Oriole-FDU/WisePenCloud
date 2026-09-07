package com.oriole.wisepen.media.api.domain.dto.req;

import com.oriole.wisepen.media.api.constant.MediaValidationMsg;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建媒体预览或播放会话请求。
 */
@Data
public class MediaPlaybackSessionCreateRequest {

    @NotBlank(message = MediaValidationMsg.RESOURCE_ID_EMPTY)
    private String resourceId;
}