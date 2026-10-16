package com.oriole.wisepen.media.api.domain.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短视频会话级 HLS JIT 任务消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaJitPlaybackTaskMessage {

    /** 需要执行 JIT 处理的水印播放会话 ID。 */
    private String sessionId;

    private String mediaId;

    private String resourceId;
}