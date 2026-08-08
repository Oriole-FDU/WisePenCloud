package com.oriole.wisepen.user.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.GroupCreateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupDeleteRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberJoinRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupDetailInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupItemInfoResponse;
import com.oriole.wisepen.user.api.enums.GroupRoleFilter;

import java.util.Map;
import java.util.Set;

public interface IGroupService {

    // 根据 groupId 获取小组详细信息
    GroupDetailInfoResponse getGroupDetailInfoById(Long groupId);
    // 根据 groupId 获取小组基础信息
    GroupItemInfoResponse getGroupBaseInfoById(Long groupId);
    // 根据 groupIds 列表获取小组展示信息
    Map<Long, GroupDisplayBase> getGroupDisplayInfoByIds(Set<Long> groupIds);

    // 创建群组
    Long createGroup(GroupCreateRequest req, Long userId);
    // 加入群组
    void joinGroup(GroupMemberJoinRequest req, Long userId, Set<Long> userJoinedGroupIds);

    // 更新群组基础信息
    void updateGroup(GroupUpdateRequest req);

    // 删除群组
    void deleteGroup(Long userId, GroupDeleteRequest req);

    // 获取指定用户的群组分页列表
    PageR<GroupItemInfoResponse> getGroupList(Long userId, GroupRoleFilter groupRoleFilter, GroupType groupType, int page, int size);
}
