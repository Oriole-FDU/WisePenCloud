package com.oriole.wisepen.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.dto.MemberListQueryResp;
import com.oriole.wisepen.user.api.domain.dto.PageResp;
import com.oriole.wisepen.user.domain.entity.*;
import com.oriole.wisepen.user.domain.enums.GroupIdentity;
import com.oriole.wisepen.user.exception.GroupErrorCode;
import com.oriole.wisepen.user.mapper.*;
import com.oriole.wisepen.user.service.GroupMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl implements GroupMemberService {

	private final GroupMapper groupMapper;
	private final GroupMemberMapper groupMemberMapper;
	private final UserMapper userMapper;
	private final UserProfileMapper userProfileMapper;
	private final GroupMemberQuotasMapper groupMemberQuotasMapper;

	public Boolean validateIsExisted(Long groupId){
		return groupMapper.selectById(groupId) != null;
	}

	private GroupMember findGroupMemberByGroupId(Long userId, Long groupId){
		LambdaQueryWrapper<GroupMember> queryWrapper = new LambdaQueryWrapper<GroupMember>()
				.eq(GroupMember::getGroupId, groupId)
				.eq(GroupMember::getUserId, userId);
		return groupMemberMapper.selectOne(queryWrapper);
	}

	private void insertGroupMember(GroupMember groupMember, Integer type){
		groupMemberMapper.insert(groupMember);

		if (type==2||type==3) {
			GroupMemberQuotas groupMemberQuotas = new GroupMemberQuotas();
			groupMemberQuotas.setId(groupMember.getId());
			groupMemberQuotas.setQuotaUsed(0);
			groupMemberQuotas.setQuotaLimit(0);
		}
	}

	@Override
	public void joinGroup(Long userId, String inviteCode) {
		LambdaQueryWrapper<Group> queryWrapper = new LambdaQueryWrapper<Group>()
				.eq(Group::getInviteCode, inviteCode);
		Group group=groupMapper.selectOne(queryWrapper);

		if (group==null||group.getDelFlag()==1){
			throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
		}

		LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, group.getId()).eq(GroupMember::getUserId, userId);
		if (groupMemberMapper.selectCount(wrapper)>0){
			throw new ServiceException(GroupErrorCode.MEMBER_IS_EXISTED);
		}
		GroupMember groupMember=new GroupMember();
		groupMember.setGroupId(group.getId());
		groupMember.setUserId(userId);
		//初始设为 member
		groupMember.setRole(GroupIdentity.MEMBER.getCode());

		insertGroupMember(groupMember,group.getType());
	}

	@Override
	public void becomeGroupOwner(Long userId, Long groupId) {
		Group group=groupMapper.selectById(groupId);

		GroupMember groupMember=new GroupMember();
		groupMember.setGroupId(groupId);
		groupMember.setUserId(userId);
		//新建时默认为 OWNER
		groupMember.setRole(GroupIdentity.OWNER.getCode());

		insertGroupMember(groupMember,group.getType());
	}

	@Override
	public void leaveGroup(Long userId, Long groupId) {
		if (!validateIsExisted(groupId)) {
			throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
		}
		Group group = groupMapper.selectById(groupId);
		GroupMember groupMember=findGroupMemberByGroupId(userId,groupId);

		if (groupMember==null) {
			throw new ServiceException(GroupErrorCode.MEMBER_NOT_IN_GROUP);
		}

		if (group.getOwnerId().equals(userId)) {
			throw new ServiceException(GroupErrorCode.MEMBER_IS_OWNER);
		}

		groupMemberMapper.deleteById(groupMember);
	}

	@Override
	public void kickGroupMember(Long userId, Long groupId, Long targetUserId) {
		if (!validateIsExisted(groupId)) {
			throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
		}

		if (targetUserId.equals(userId)){
			throw new ServiceException(GroupErrorCode.DO_NOT_UPDATE_YOURSELF);
		}

		GroupMember groupMember=findGroupMemberByGroupId(userId,groupId);
		if (groupMember==null) {
			throw new ServiceException(GroupErrorCode.MEMBER_NOT_IN_GROUP);
		}
		GroupMember targetGroupMember=findGroupMemberByGroupId(targetUserId,groupId);
		if (targetGroupMember==null) {
			throw new ServiceException(GroupErrorCode.MEMBER_NOT_IN_GROUP);
		}

		//权限不够，权限高的操控权限低的
		if (targetGroupMember.getRole()<=groupMember.getRole()) {
			throw new ServiceException(GroupErrorCode.PERMISSION_IS_LOWER);
		}

		groupMemberMapper.deleteById(targetGroupMember);
	}

	@Override
	public void kickGroupMembers(Long operatorUserId, Long groupId, List<Long> targetUserIds) {
		for (Long targetUserId : targetUserIds) {
			// 复用你原来的单个踢人逻辑
			kickGroupMember(operatorUserId, groupId, targetUserId);
		}
	}

	@Override
	public PageResp<MemberListQueryResp> getMemberList(Long groupId, Integer page, Integer size) {
		if (!validateIsExisted(groupId)) {
			throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
		}

		Group group=groupMapper.selectById(groupId);

		Page<GroupMember> mpPage = new Page<>(page, size);
		LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<GroupMember>()
				.eq(GroupMember::getGroupId,groupId)
				.select(GroupMember::getUserId,GroupMember::getGroupId,GroupMember::getRole,GroupMember::getJoinTime);

		IPage<GroupMember> memberRecords = groupMemberMapper.selectPage(mpPage, wrapper);
		List<Long> ids = memberRecords.getRecords().stream()
				.map(GroupMember::getUserId)
				.distinct()
				.toList();

		if (ids.isEmpty()) {
			return new PageResp<>((int) memberRecords.getPages(), Collections.emptyList());
		}
		List<User> users=userMapper.selectBatchIds(ids);
		List<UserProfile> userProfiles=userProfileMapper.selectBatchIds(ids);

		Map<Long, User> userMap = users.stream()
				.collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

		Map<Long, UserProfile> userProfileMap = userProfiles.stream()
				.collect(Collectors.toMap(UserProfile::getUserId, u -> u, (a, b) -> a));

		List<MemberListQueryResp> records = memberRecords.getRecords().stream().map(gm -> {
			MemberListQueryResp resp = new MemberListQueryResp();
			Long uid = gm.getUserId();

			resp.setUserId(uid);
			resp.setRole(gm.getRole());
			resp.setJoinTime(gm.getJoinTime());

			User u = userMap.get(uid);
			if (u != null) {
				resp.setNickname(u.getNickname());
			}
			//普通组不显示 real_name
			if (group.getType()!=1) {
				UserProfile uu = userProfileMap.get(uid);
				if (uu != null) {
					resp.setRealname(uu.getRealName());
				}
			}
			return resp;
		}).toList();

//		records.sort(
//				Comparator.comparing(MemberListQueryResp::getRole, Comparator.nullsLast(Comparator.naturalOrder()))
//						.thenComparing(MemberListQueryResp::getJoinTime, Comparator.nullsLast(Comparator.naturalOrder()))
//		);
		//加一个按照role和joinTime排序的操作。
//		List<MemberListQueryResp> records=BeanUtil.copyToList(users,MemberListQueryResp.class);
		return new PageResp<>((int) memberRecords.getPages(), records);
	}

	@Override
	public void updateGroupMemberRole(Long groupId, Long targetUserId, Integer role) {
		Long userId=SecurityContextHolder.getUserId();
		if (targetUserId.equals(userId)){
			throw new ServiceException(GroupErrorCode.DO_NOT_UPDATE_YOURSELF);
		}
		GroupMember targetGroupMember = findGroupMemberByGroupId(targetUserId, groupId);
		if (targetGroupMember==null) {
			throw new ServiceException(GroupErrorCode.MEMBER_NOT_IN_GROUP);
		}

		GroupMember groupMember=findGroupMemberByGroupId(userId,groupId);
		if (groupMember==null||!groupMember.getRole().equals(GroupIdentity.OWNER.getCode())) {
			throw new ServiceException(GroupErrorCode.PERMISSION_IS_LOWER);
		}
		LambdaUpdateWrapper<GroupMember> wrapper = new LambdaUpdateWrapper<GroupMember>()
				.eq(GroupMember::getId,targetGroupMember.getId())
				.set(GroupMember::getRole,role);
		groupMemberMapper.update(wrapper);
	}
}
