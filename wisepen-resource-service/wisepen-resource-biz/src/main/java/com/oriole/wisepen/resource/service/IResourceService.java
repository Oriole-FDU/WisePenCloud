package com.oriole.wisepen.resource.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.list.QueryLogicEnum;
import com.oriole.wisepen.common.core.domain.enums.list.SortDirectionEnum;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.domain.dto.*;
import com.oriole.wisepen.resource.domain.dto.req.ResourceRenameRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourceUpdateActionPermissionRequest;
import com.oriole.wisepen.resource.domain.dto.res.ResourceBaseInfoResponse;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.domain.entity.TagEntity;
import com.oriole.wisepen.resource.enums.ResourceSortBy;
import com.oriole.wisepen.resource.enums.ResourceTagUpdateMode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IResourceService {

    // 断言资源归某用户所有
    void assertResourceOwner(String resourceId, String userId);

    void assertResourceOwner(List<String> resourceIds, String userId);

    ResourceItemEntity getResourceEntity(String resourceId);

    List<Long> listResourceCountableGroupIds(List<GroupTagBind> groupTagBinds, Map<Long, GroupRoleType> operatorGroupRoles);

    // ToUser：重命名、变更Tag、列出资源
    void renameResource(ResourceRenameRequest req);

    List<GroupTagBind> updateResourceGroupBinds(List<GroupTagBind> groupBinds, String groupId, List<String> tagIds);

    void updatePersonalResourceTags(List<String> resourceIds, String groupId, List<String> tagIds, ResourceTagUpdateMode mode);

    void updateGroupResourceTags(List<String> resourceIds, String groupId, String userId, GroupRoleType groupRole, List<String> tagIds, ResourceTagUpdateMode mode);

    List<TagEntity> findAndValidateTags(String groupId, List<String> tagIds);

    void updateResourceActionPermission(ResourceUpdateActionPermissionRequest req);

    ResourceBaseInfoResponse getResourceBaseInfo(String resourceId, String currentUserId, Map<Long, GroupRoleType> groupRoles);

    PageR<ResourceItemResponse> listResources(String currentUserId,
                                              String groupId, GroupRoleType userGroupRole, Map<Long, GroupRoleType> groupRoles,
                                              List<String> tagIds, QueryLogicEnum tagQueryLogicMode,
                                              Boolean includeMyInteraction,
                                              String resourceType, int page, int size,
                                              ResourceSortBy sortBy, SortDirectionEnum sortDir);

    void softRemoveResources(List<String> resourceIds);

    // 内部：标签节点变更后、标签节点删除后（权限重新计算/移除标签）；权限重新计算
    void afterTagNodeChanged(List<String> changedTagIds, Boolean isPersonalTag);

    void afterTagNodeDeleted(List<String> deletedTagIds, Boolean isPersonalTag, Boolean isPathTag);

    Optional<ResourceItemEntity> calculateResourceGroupAcl(String resourceId);

    // ToService：增加、移除、更新资源；检查特定资源的权限

    String createResourceItem(ResourceCreateReqDTO dto);

    void hardRemoveResources(List<String> resourceIds);

    List<ResourceItemInfoResDTO> listResourceBaseInfo(List<String> resourceIds);

    void updateResourceAttributes(ResourceUpdateReqDTO dto);

    ResourceItemResponse getResourceInfo(ResourceInfoGetReqDTO dto);

    ResourceCheckPermissionResDTO checkPermission(ResourceCheckPermissionReqDTO dto);

    void stripGroupPermission(List<String> trashedTagIds);
}
