package com.oriole.wisepen.resource.service;

import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.list.QueryLogicEnum;
import com.oriole.wisepen.common.core.domain.enums.list.SortDirectionEnum;
import com.oriole.wisepen.resource.domain.dto.*;
import com.oriole.wisepen.resource.enums.ResourceSortByEnum;

import java.util.List;

public interface IResourceService {

    // 断言资源归某用户所有
    void assertResourceOwner(String resourceId, String userId);

    // ToUser：重命名、变更Tag、列出资源
    void renameResource(ResourceRenameRequest req);

    void updateResourceTags(ResourceUpdateTagsRequest req);

    PageResult<ResourceItemResponse> listResources(String currentUserId,
                                                   String groupId, GroupRoleType userGroupRole,
                                                   List<String> tagIds, QueryLogicEnum tagQueryLogicMode,
                                                   String resourceType, int page, int size,
                                                   ResourceSortByEnum sortBy, SortDirectionEnum sortDir);

    // 内部：标签节点变更后、标签节点删除后（权限重新计算/移除标签）；权限重新计算
    void afterTagNodeChanged(List<String> changedTagIds);

    void afterTagNodeDeleted(List<String> deletedTagIds, Boolean isPersonalTag);

    void calculateResourceAcl(String resourceId);

    // ToService：增加、移除、更新资源；检查特定资源的权限

    String createResourceItem(ResourceCreateDTO dto);

    void removeResourceItem(String resourceId);

    void updateResourceAttributes(ResourceUpdateDTO dto);

    Boolean checkPermission(ResourceCheckPermissionDTO dto);
}