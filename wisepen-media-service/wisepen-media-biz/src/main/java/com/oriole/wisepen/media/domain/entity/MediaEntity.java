package com.oriole.wisepen.media.domain.entity;

import com.oriole.wisepen.media.api.domain.base.MediaBase;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wisepen_media_entities")
@CompoundIndex(
        name = "idx_resource_id",
        def = "{'resourceId': 1}",
        unique = true,
        partialFilter = "{'resourceId': {'$exists': true, '$ne': null}}"
)
public class MediaEntity extends MediaBase implements Persistable<String> {

    @Id
    private String mediaId;

    /** 资源 ID */
    private String resourceId;

    /** 源文件在 OSS 中的 ObjectKey */
    private String sourceObjectKey;

    /** 图片预览图或视频封面图在 OSS 中的 ObjectKey */
    private String previewObjectKey;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaHlsRenditionObjectKeys {
        private String playlistName;
        private String initObjectKey;
        /** 顺序与 MediaHlsRendition.segmentDurations 一致 */
        private List<String> segmentObjectKeys;
    }

    /** HLS 各档对象存储 Object Keys */
    private List<MediaHlsRenditionObjectKeys> hlsObjectKeys;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;

    /** 汇总全部 ObjectKey（源文件/封面/HLS init 与分片），用于删除时精确清理 */
    public List<String> collectObjectKeys() {
        List<String> keys = new ArrayList<>();
        if (sourceObjectKey != null && !sourceObjectKey.isEmpty()) {
            keys.add(sourceObjectKey);
        }
        if (previewObjectKey != null && !previewObjectKey.isEmpty()) {
            keys.add(previewObjectKey);
        }
        if (hlsObjectKeys != null) {
            for (MediaHlsRenditionObjectKeys rendition : hlsObjectKeys) {
                if (rendition == null) {
                    continue;
                }
                if (rendition.getInitObjectKey() != null && !rendition.getInitObjectKey().isEmpty()) {
                    keys.add(rendition.getInitObjectKey());
                }
                if (rendition.getSegmentObjectKeys() != null) {
                    for (String key : rendition.getSegmentObjectKeys()) {
                        if (key != null && !key.isEmpty()) {
                            keys.add(key);
                        }
                    }
                }
            }
        }
        return keys;
    }

    @Override
    public String getId() {
        return mediaId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return createTime == null;
    }
}
