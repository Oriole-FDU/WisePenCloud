package com.oriole.wisepen.resource.service;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;

import java.util.List;
import java.util.Map;

public interface IResourcePlacementService {

    void setPersonalResourcesPathTag(List<String> resourceIds, String userId, String targetPathTagId);

    void movePersonalResourcesToTrash(List<String> resourceIds, String userId);

    void replacePersonalNormalTags(List<String> resourceIds, String userId, List<String> normalTagIds);

    void mountResourcesToGroup(List<String> resourceIds, String groupId, String userId, GroupRoleType groupRole, String targetTagId);

    void unmountResourcesToGroup(Map<String, String> resourceSourceTagMap, String groupId, String userId, GroupRoleType groupRole);

    void moveResourcesInGroup(Map<String, String> resourceSourceTagMap, String groupId, String userId, GroupRoleType groupRole, String targetTagId);
}
