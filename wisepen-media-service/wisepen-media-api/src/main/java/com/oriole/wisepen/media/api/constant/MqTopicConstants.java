package com.oriole.wisepen.media.api.constant;

/**
 * Media 服务 Kafka Topic 常量。
 */
public interface MqTopicConstants {

    /** 媒体上传后的处理任务。 */
    String TOPIC_MEDIA_PROCESS = "wisepen-media-process-topic";

    /** 媒体处理死信任务。 */
    String TOPIC_MEDIA_PROCESS_DLQ = TOPIC_MEDIA_PROCESS + ".DLQ";

    /** 短视频会话级 HLS JIT 处理任务。 */
    String TOPIC_MEDIA_JIT_PLAYBACK = "wisepen-media-jit-playback-topic";

    /** 媒体处理就绪事件。 */
    String TOPIC_MEDIA_READY = "wisepen-media-ready-topic";
}
