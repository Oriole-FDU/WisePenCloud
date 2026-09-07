package com.oriole.wisepen.media.domain.entity;

import com.oriole.wisepen.media.api.enums.MediaDeliveryMode;
import com.oriole.wisepen.media.api.enums.WatermarkCapabilityStatus;
import com.oriole.wisepen.media.api.enums.WatermarkSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wisepen_media_watermark_sessions")
public class MediaWatermarkSessionEntity implements Persistable<String> {

    @Id
    private String sessionId;

    /** 取证水印 ID，用于从泄露产物反查会话、资源和观看者。 */
    @Indexed(unique = true)
    private String wmId;

    /** 创建或访问该水印播放会话的用户 ID。 */
    private Long viewerId;

    private String resourceId;

    private String mediaId;

    /** 会话创建或访问时间。 */
    private LocalDateTime accessedAt;

    /** 会话过期时间。 */
    private LocalDateTime expiresAt;

    private String watermarkText;

    private MediaDeliveryMode deliveryMode;

    private WatermarkSessionStatus status;

    private WatermarkCapabilityStatus forensicStatus;

    /** 图片水印预览产物在 OSS 中的 ObjectKey。 */
    private String previewObjectKey;

    /** 视频水印 HLS manifest 在 OSS 中的 ObjectKey。 */
    private String manifestObjectKey;

    /** 本会话生成或授权的全部交付产物 ObjectKey，用于资源删除时清理。 */
    private List<String> deliveryObjectKeys;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;

    @Override
    public String getId() {
        return sessionId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return createTime == null;
    }
}
