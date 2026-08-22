package com.oriole.wisepen.media.domain.entity;

import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wisepen_media_info")
@CompoundIndex(
        name = "idx_resource_id",
        def = "{'resourceId': 1}",
        unique = true,
        partialFilter = "{'resourceId': {'$exists': true, '$ne': null}}"
)
public class MediaInfoEntity implements Persistable<String> {

    @Id
    private String mediaId;

    private String resourceId;

    private Long ownerId;

    private ResourceType resourceType;

    private String originalFilename;

    private String sourceExtension;

    /** 源文件在 OSS 中的 ObjectKey */
    private String sourceObjectKey;

    /** 视频源 HLS 在 OSS 中的目录前缀 */
    private String sourceHlsPrefix;

    /** 视频源 HLS 具体文件 ObjectKey 列表，用于资源删除时精确清理 */
    private List<String> sourceHlsObjectKeys;

    /** 图片预览图或视频封面图在 OSS 中的 ObjectKey */
    private String previewObjectKey;

    private String mountTargetTagId;
    private Map<Long, GroupRoleType> uploaderGroupRoles;

    private Long durationMs;

    private Integer width;

    private Integer height;

    private Long size;

    private MediaStatus mediaStatus;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;

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
