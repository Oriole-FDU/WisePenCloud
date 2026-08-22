package com.oriole.wisepen.media.domain;

import com.oriole.wisepen.media.api.enums.MediaDeliveryMode;
import com.oriole.wisepen.media.api.enums.WatermarkCapabilityStatus;
import com.oriole.wisepen.media.api.enums.WatermarkSessionStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Provider 返回的播放授权内部结果。
 */
@Data
@Builder
public class MediaPlaybackGrant {

    /** Provider 返回的会话状态。 */
    private WatermarkSessionStatus status;

    /** Provider 选择的播放交付模式。 */
    private MediaDeliveryMode deliveryMode;

    /** Provider 返回的暗水印取证能力状态。 */
    private WatermarkCapabilityStatus capabilityStatus;

    /** 图片预览产物在 OSS 中的 ObjectKey。 */
    private String previewObjectKey;

    /** 视频 HLS manifest 在 OSS 中的 ObjectKey。 */
    private String manifestObjectKey;

    /** 本次播放授权关联的全部可删除交付产物 ObjectKey。 */
    private List<String> deliveryObjectKeys;

    /** Provider 建议客户端再次轮询的等待时间，单位毫秒。 */
    private Long retryAfterMs;
}
