package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.*;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberDetailResponse;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.*;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.mapper.*;
import com.oriole.wisepen.user.service.IDisplayService;
import com.oriole.wisepen.user.service.IGroupMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl implements IGroupMemberService {

	private final GroupMemberMapper groupMemberMapper;
	private final GroupMapper groupMapper;
	private final IDisplayService displayService;
	private final RedisCacheManager redisCacheManager;

	@Override
	public Map<String, Integer> getGroupRoleMapByUserId(Long userId) {
		List<GroupMemberEntity> members = groupMemberMapper.selectList(
				new LambdaQueryWrapper<GroupMemberEntity>()
						.eq(GroupMemberEntity::getUserId, userId)
						.select(GroupMemberEntity::getGroupId, GroupMemberEntity::getRole)
		);
		if (CollectionUtils.isEmpty(members)) {
			return Collections.emptyMap();
		}
		return members.stream()
				.collect(Collectors.toMap(
						member -> String.valueOf(member.getGroupId()),
						member -> member.getRole().getCode()
				));
	}

	@Override
	public void joinGroup(Long groupId, Long userId, GroupRoleType groupRoleType) {
		GroupMemberEntity member = GroupMemberEntity.builder()
				.groupId(groupId).userId(userId)
				.role(groupRoleType).joinTime(LocalDateTime.now())
				.tokenLimit(0).tokenUsed(0)
				.build();
		groupMemberMapper.insert(member);

		// 更新 Redis
		redisCacheManager.updateGroupRoleMapInSession(userId, groupId, groupRoleType);
	}

	@Override
	public void quitGroup(GroupMemberQuitRequest req, Long userId, GroupRoleType opGroupRoleType) {
		if (GroupRoleType.OWNER.equals(opGroupRoleType)) {
			throw new ServiceException(UserError.CANNOT_QUIT_GROUP_AS_OWNER); // 群主不可直接退群
		}

		LambdaQueryWrapper<GroupMemberEntity> deleteWrapper = new LambdaQueryWrapper<>();
		deleteWrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
				.eq(GroupMemberEntity::getUserId, userId);
		groupMemberMapper.delete(deleteWrapper);

		// 更新 Redis
		redisCacheManager.updateGroupRoleMapInSession(userId, req.getGroupId(), GroupRoleType.NOT_MEMBER);
	}

	@Override
	public void kickGroupMembers(GroupMemberKickRequest req, Long opUserId, GroupRoleType opGroupRoleType) {
		Set<Long> targetUserIdSet = req.getTargetUserIds().stream()
				.filter(id -> !id.equals(opUserId))
				.collect(Collectors.toSet()); // 不能踢自己

		if (targetUserIdSet.isEmpty()) {
			throw new ServiceException(UserError.GROUP_MEMBER_NOT_FOUND);
		}

		LambdaQueryWrapper<GroupMemberEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
				.in(GroupMemberEntity::getUserId, targetUserIdSet);
		List<GroupMemberEntity> targetMembers = groupMemberMapper.selectList(wrapper);

		if (targetMembers.isEmpty()) {
			throw new ServiceException(UserError.GROUP_MEMBER_NOT_FOUND);
		}

		List<Long> validUserIdsToDelete = new ArrayList<>(); // 收集符合踢出条件的 userId

		for (GroupMemberEntity target : targetMembers) {
			// 权限数值越小权限越大 (如 OWNER=1, ADMIN=2, MEMBER=3)，操作者的权限必须严格高于被踢者
			if (opGroupRoleType.getCode() < target.getRole().getCode()) {
				validUserIdsToDelete.add(target.getUserId());
			}
		}

		if (validUserIdsToDelete.isEmpty()) {
			throw new ServiceException(UserError.GROUP_MEMBER_NOT_FOUND);
		}

		LambdaQueryWrapper<GroupMemberEntity> deleteWrapper = new LambdaQueryWrapper<>();
		deleteWrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
				.in(GroupMemberEntity::getUserId, validUserIdsToDelete);
		groupMemberMapper.delete(deleteWrapper);

		// 更新 Redis
		validUserIdsToDelete.forEach(targetId ->
				redisCacheManager.updateGroupRoleMapInSession(targetId, req.getGroupId(), GroupRoleType.NOT_MEMBER)
		);
	}

	@Override
	public GroupMemberDetailResponse getGroupMemberInfoByUserId(Long groupId, Long userId){
		GroupEntity groupEntity = groupMapper.selectById(groupId);
		if (groupEntity == null) {
			throw new ServiceException(UserError.GROUP_NOT_EXIST);
		}

		LambdaQueryWrapper<GroupMemberEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(GroupMemberEntity::getGroupId, groupId)
				.eq(GroupMemberEntity::getUserId, userId);

		GroupMemberEntity memberEntity = groupMemberMapper.selectOne(wrapper);

		if (memberEntity == null) {
			throw new ServiceException(UserError.GROUP_MEMBER_NOT_FOUND);
		}

		boolean includePrivateFields = GroupType.ADVANCED_GROUP.equals(groupEntity.getGroupType());
		UserDisplayBase userInfo = displayService.getUserDisplayInfoByIds(Set.of(userId), includePrivateFields).get(userId);

		GroupMemberDetailResponse resp = new GroupMemberDetailResponse();
		BeanUtil.copyProperties(memberEntity, resp);
		resp.setMemberId(memberEntity.getUserId());
		resp.setMemberInfo(userInfo);
		return resp;
	}

	@Override
	public PageR<GroupMemberDetailResponse> getGroupMemberList(Long groupId, int page, int size) {
		GroupEntity groupEntity = groupMapper.selectById(groupId);
		if (groupEntity == null) {
			throw new ServiceException(UserError.GROUP_NOT_EXIST);
		}

		Page<GroupMemberEntity> pageParam = new Page<>(page, size);
		LambdaQueryWrapper<GroupMemberEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(GroupMemberEntity::getGroupId, groupId)
				// 按角色升序(OWNER排前面)，再按加入时间降序(新入群的在前面)
				.orderByAsc(GroupMemberEntity::getRole)
				.orderByDesc(GroupMemberEntity::getJoinTime);

		IPage<GroupMemberEntity> memberPage = groupMemberMapper.selectPage(pageParam, wrapper);

		// 提取当前页所有的 userId
		Set<Long> userIds = memberPage.getRecords().stream()
				.map(GroupMemberEntity::getUserId)
				.collect(Collectors.toSet());

		PageR<GroupMemberDetailResponse> pageR = new PageR<>(memberPage.getTotal(), page, size);

		boolean includePrivateFields = GroupType.ADVANCED_GROUP.equals(groupEntity.getGroupType());
		Map<Long, UserDisplayBase> userMap = displayService.getUserDisplayInfoByIds(userIds, includePrivateFields);

		List<GroupMemberDetailResponse> records = memberPage.getRecords().stream().map(memberEntity -> {
			GroupMemberDetailResponse resp = new GroupMemberDetailResponse();
			BeanUtil.copyProperties(memberEntity, resp);
			resp.setMemberId(memberEntity.getUserId());
			resp.setMemberInfo(userMap.get(memberEntity.getUserId()));
			return resp;
		}).collect(Collectors.toList());

		pageR.addAll(records);
		return pageR;
	}

	@Override
	public List<Long> listGroupMemberIds(Long groupId) {
		LambdaQueryWrapper<GroupMemberEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(GroupMemberEntity::getGroupId, groupId)
				.orderByAsc(GroupMemberEntity::getRole)
				.orderByDesc(GroupMemberEntity::getJoinTime)
				.select(GroupMemberEntity::getUserId);
		return groupMemberMapper.selectList(wrapper).stream()
				.map(GroupMemberEntity::getUserId)
				.collect(Collectors.toList());
	}

	@Override
	public void updateGroupMemberRole(GroupMemberRoleUpdateRequest req, Long opUserId) {
		Set<Long> targetUserIdSet = req.getTargetUserIds().stream()
				.filter(id -> !id.equals(opUserId))
				.collect(Collectors.toSet()); // 不能更新自己的权限

		if (targetUserIdSet.isEmpty()) {
			throw new ServiceException(UserError.GROUP_MEMBER_NOT_FOUND);
		}

		LambdaQueryWrapper<GroupMemberEntity> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
				.in(GroupMemberEntity::getUserId, targetUserIdSet);
		List<GroupMemberEntity> existMembers = groupMemberMapper.selectList(queryWrapper);

		if (existMembers.isEmpty()) {
			throw new ServiceException(UserError.GROUP_MEMBER_NOT_FOUND);
		}

		List<Long> actualUserIdsToUpdate = existMembers.stream()
				.map(GroupMemberEntity::getUserId)
				.collect(Collectors.toList());

		LambdaUpdateWrapper<GroupMemberEntity> wrapper = new LambdaUpdateWrapper<>();
		wrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
				.in(GroupMemberEntity::getUserId, actualUserIdsToUpdate)
				.set(GroupMemberEntity::getRole, req.getRole());

		groupMemberMapper.update(null, wrapper);

		actualUserIdsToUpdate.forEach(targetId ->
						redisCacheManager.updateGroupRoleMapInSession(targetId, req.getGroupId(), req.getRole())
		);
	}

	@Override
	public void removeAllGroupMembers(Long groupId) {
		LambdaQueryWrapper<GroupMemberEntity> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(GroupMemberEntity::getGroupId, groupId);
		List<GroupMemberEntity> allMembers = groupMemberMapper.selectList(queryWrapper);

		groupMemberMapper.delete(queryWrapper);

		// 3. 精准同步：遍历所有被踢出的成员，将他们的 Redis 状态置为 NOT_MEMBER
		allMembers.forEach(member ->
				redisCacheManager.updateGroupRoleMapInSession(member.getUserId(), groupId, GroupRoleType.NOT_MEMBER)
		);
	}
}
