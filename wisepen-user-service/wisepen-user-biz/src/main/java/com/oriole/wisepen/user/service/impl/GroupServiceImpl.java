package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.GroupCreateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupDeleteRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberJoinRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupDetailInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupItemInfoResponse;
import com.oriole.wisepen.user.api.enums.GroupRoleFilter;
import com.oriole.wisepen.user.api.enums.TokenTransferType;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.GroupEntity;
import com.oriole.wisepen.user.domain.entity.GroupMemberEntity;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import com.oriole.wisepen.user.service.IGroupMemberService;
import com.oriole.wisepen.user.service.IGroupService;
import com.oriole.wisepen.user.service.IUserService;
import com.oriole.wisepen.user.service.IWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements IGroupService {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final IUserService userService;
    private final IGroupMemberService groupMemberService;
    private final IWalletService walletService;
    private final RedisCacheManager redisCacheManager;
    private final RemoteResourceService remoteResourceService;

    @Override
    public void joinGroup(GroupMemberJoinRequest req, Long userId, Set<Long> userJoinedGroupIds) {
        LambdaQueryWrapper<GroupEntity> queryWrapper = new LambdaQueryWrapper<GroupEntity>()
                .eq(GroupEntity::getInviteCode, req.getInviteCode());
        GroupEntity group = groupMapper.selectOne(queryWrapper);
        if (group == null) {
            throw new ServiceException(UserError.GROUP_NOT_EXIST);
        }

        if (userJoinedGroupIds.contains(group.getGroupId())) {
            throw new ServiceException(UserError.GROUP_MEMBER_ALREADY_EXISTS);
        }

        groupMemberService.joinGroup(group.getGroupId(), userId, GroupRoleType.MEMBER);
    }

    @Override
    public Long createGroup(GroupCreateRequest req, Long userId) {
        GroupEntity group = GroupEntity.builder()
                .ownerId(userId)
                .inviteCode(IdUtil.fastSimpleUUID().substring(0, 8))
                .tokenUsed(0)
                .tokenBalance(0)
                .build();

        BeanUtil.copyProperties(req, group, "ownerId", "inviteCode", "tokenUsed", "tokenBalance");
        groupMapper.insert(group);
        groupMemberService.joinGroup(group.getGroupId(), userId, GroupRoleType.OWNER);
        redisCacheManager.blockGroupChat(group.getGroupId());
        return group.getGroupId();
    }

    @Override
    public void updateGroup(GroupUpdateRequest req) {
        GroupEntity group = BeanUtil.copyProperties(req, GroupEntity.class);
        group.setUpdateTime(LocalDateTime.now());
        int rows = groupMapper.updateById(group);
        if (rows == 0) {
            throw new ServiceException(UserError.GROUP_NOT_EXIST);
        }
    }

    @Override
    public void deleteGroup(Long userId, GroupDeleteRequest req) {
        Long groupId = req.getGroupId();
        GroupEntity group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new ServiceException(UserError.GROUP_NOT_EXIST);
        }
        if (group.getGroupType()==GroupType.ADVANCED_GROUP) {
            walletService.transferTokenBetweenGroupAndUser(userId, groupId, group.getTokenBalance(), TokenTransferType.USER_INFLOW);
        }
        groupMapper.deleteById(groupId);
        groupMemberService.removeAllGroupMembers(groupId);

        // 通知资源微服务删除该小组的 Tag 树与资源配置
        try {
            remoteResourceService.dissolveGroup(groupId);
        } catch (Exception e) {
            log.error("group dissolve notify failed. groupId={} dependency=resourceService", groupId, e);
        }
    }

    @Override
    public PageR<GroupItemInfoResponse> getGroupList(Long userId, GroupRoleFilter groupRoleFilter, GroupType groupType, int page, int size) {
        LambdaQueryWrapper<GroupMemberEntity> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(GroupMemberEntity::getUserId, userId)
                .select(GroupMemberEntity::getGroupId);
        if (groupRoleFilter == GroupRoleFilter.ALL) {
            memberWrapper.in(GroupMemberEntity::getRole, GroupRoleType.ADMIN, GroupRoleType.OWNER, GroupRoleType.MEMBER);
        } else if (groupRoleFilter == GroupRoleFilter.MANAGED) {
            memberWrapper.in(GroupMemberEntity::getRole, GroupRoleType.ADMIN, GroupRoleType.OWNER);
        } else {
            memberWrapper.in(GroupMemberEntity::getRole, GroupRoleType.MEMBER);
        }

        List<Long> groupIds = groupMemberMapper.selectList(memberWrapper).stream()
                .map(GroupMemberEntity::getGroupId)
                .collect(Collectors.toList());
        if (groupIds.isEmpty()) {
            return new PageR<>(0, page, size);
        }

        LambdaQueryWrapper<GroupEntity> groupWrapper = new LambdaQueryWrapper<GroupEntity>()
                .eq(groupType != null, GroupEntity::getGroupType, groupType)
                .in(GroupEntity::getGroupId, groupIds);
        Page<GroupEntity> resultPage = groupMapper.selectPage(new Page<>(page, size), groupWrapper);

        PageR<GroupItemInfoResponse> pageR = new PageR<>(resultPage.getTotal(), page, size);
        List<GroupEntity> groups = resultPage.getRecords();
        Set<Long> ownerIds = groups.stream().map(GroupEntity::getOwnerId).collect(Collectors.toSet());
        Map<Long, UserDisplayBase> ownerMap = userService.getUserDisplayInfoByIds(ownerIds);

        List<GroupItemInfoResponse> responses = groups.stream().map(g -> {
            GroupItemInfoResponse resp = BeanUtil.copyProperties(g, GroupItemInfoResponse.class);
            resp.setOwnerInfo(ownerMap.get(g.getOwnerId()));
            return resp;
        }).collect(Collectors.toList());

        pageR.addAll(responses);
        return pageR;
    }

    public GroupEntity getGroupInfoById(Long groupId) {
        GroupEntity group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new ServiceException(UserError.GROUP_NOT_EXIST);
        }
        return group;
    }

    @Override
    public GroupItemInfoResponse getGroupBaseInfoById(Long groupId) {
        GroupEntity group = getGroupInfoById(groupId);
        GroupItemInfoResponse resp = BeanUtil.copyProperties(group, GroupItemInfoResponse.class);
        resp.setOwnerInfo(userService.getUserDisplayInfoByIds(Set.of(group.getOwnerId())).get(group.getOwnerId()));
        return resp;
    }

    @Override
    public GroupDetailInfoResponse getGroupDetailInfoById(Long groupId) {
        GroupEntity group = getGroupInfoById(groupId);
        GroupDetailInfoResponse resp = BeanUtil.copyProperties(group, GroupDetailInfoResponse.class);
        resp.setOwnerInfo(userService.getUserDisplayInfoByIds(Set.of(group.getOwnerId())).get(group.getOwnerId()));
        return resp;
    }

    @Override
    public Map<Long, GroupDisplayBase> getGroupDisplayInfoByIds(Set<Long> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyMap();
        }
        List<GroupEntity> groupList = groupMapper.selectBatchIds(groupIds);

        if (CollectionUtils.isEmpty(groupList)) {
            return Collections.emptyMap();
        }

        return groupList.stream().filter(Objects::nonNull).collect(Collectors.toMap(
                GroupEntity::getGroupId,
                group -> BeanUtil.copyProperties(group, GroupDisplayBase.class),
                (existing, replacement) -> existing
        ));
    }
}
