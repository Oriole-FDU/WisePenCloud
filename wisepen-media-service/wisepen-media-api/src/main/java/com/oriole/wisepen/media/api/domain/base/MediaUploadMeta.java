package com.oriole.wisepen.media.api.domain.base;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** 媒体上传上下文 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadMeta {
    private ResourceType resourceType;
    private String originalFilename;
    private String sourceExtension;
    private Long size;
    private Long uploaderId;
    private Map<Long, GroupRoleType> uploaderGroupRoles;
    private String mountTargetTagId;
}
