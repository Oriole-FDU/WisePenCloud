package com.oriole.wisepen.generic.resource.domain.entity;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.generic.resource.api.enums.GenericResourceStatusEnum;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wisepen_generic_resource_files")
@CompoundIndex(
        name = "idx_resource_id",
        def = "{'resourceId': 1}",
        unique = true,
        partialFilter = "{'resourceId': {'$exists': true, '$ne': null}}"
)
@CompoundIndex(
        name = "idx_object_key",
        def = "{'objectKey': 1}",
        unique = true,
        partialFilter = "{'objectKey': {'$exists': true, '$ne': null}}"
)
@CompoundIndex(name = "idx_resource_type", def = "{'resourceType': 1}")
public class GenericResourceFileEntity implements Persistable<String> {

    @Id
    private String uploadId;

    /** 上传完成并注册资源主档后才写入；未就绪上传任务不会出现在资源列表中。 */
    private String resourceId;
    private String resourceName;
    /** 资源业务类型，后续压缩包等类型迁出时按此字段筛选迁移。 */
    private ResourceType resourceType;
    /** 真实文件扩展名；UNKNOWN 资源也保留原扩展名，避免下载和迁移时丢失文件形态。 */
    private String extension;
    private String objectKey;
    private String md5;
    private Long size;
    private Long uploaderId;
    /** 首次注册资源时用于小组路径挂载权限判断的上传时角色快照。 */
    private Map<Long, GroupRoleType> uploaderGroupRoles;
    private String mountTargetTagId;
    private GenericResourceStatusEnum status;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;

    @Override
    public String getId() {
        return uploadId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return createTime == null;
    }
}
