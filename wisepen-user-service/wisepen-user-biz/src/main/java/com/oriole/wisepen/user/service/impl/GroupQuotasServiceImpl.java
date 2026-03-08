package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.dto.GetGroupMemberQuotasResp;
import com.oriole.wisepen.user.api.domain.dto.GetMyGroupQuotasResp;
import com.oriole.wisepen.user.api.domain.dto.GroupQueryResp;
import com.oriole.wisepen.user.api.domain.dto.PageResp;
import com.oriole.wisepen.user.component.InviteCodeGenerator;
import com.oriole.wisepen.user.domain.entity.Group;
import com.oriole.wisepen.user.domain.entity.GroupMember;
import com.oriole.wisepen.user.domain.entity.GroupMemberQuotas;
import com.oriole.wisepen.user.exception.GroupErrorCode;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.user.mapper.GroupMemberQuotasMapper;
import com.oriole.wisepen.user.mapper.GroupWalletsMapper;
import com.oriole.wisepen.user.service.GroupQuotasService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupQuotasServiceImpl implements GroupQuotasService {
	private final GroupMapper groupMapper;
	private final GroupMemberMapper groupMemberMapper;
	private final GroupWalletsMapper groupWalletsMapper;
	private final GroupMemberQuotasMapper groupMemberQuotasMapper;
	private final InviteCodeGenerator inviteCodeGenerator;

	public Boolean validateIsExisted(Long groupId){
		return groupMapper.selectById(groupId) != null;
	}

	@Override
	public PageResp<GetGroupMemberQuotasResp> getGroupMemberQuotas(Long groupId, Integer page, Integer size) {
		Group group = groupMapper.selectById(groupId);
		if (group.getType()==1) {
			throw new ServiceException(GroupErrorCode.NORMAL_GROUP);
		}
		return null;

//		List<Long> ids=
//		groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId)).stream()
//		.map(GroupMember::getId)
//		.toList();
//
//		Page<GroupMember> mpPage = new Page<>(page, size);
//
//		LambdaQueryWrapper<GroupMemberQuotas> w = new LambdaQueryWrapper<GroupMemberQuotas>()
//				.eq(GroupMemberQuotas::getGroupId, groupId);
//
//		if (type==1) {
//			w.in(GroupMember::getRole, 1,2);
//		}
//
//		IPage<GroupMember> memberPage = groupMemberMapper.selectPage(mpPage, w);
//
//		if (memberPage.getRecords().isEmpty()) {
//			return new PageResp<>((int) memberPage.getPages(), Collections.emptyList());
//		}
//		List<Long> groupIds = memberPage.getRecords().stream()
//				.map(GroupMember::getGroupId)
//				.distinct()
//				.toList();
//
//
//		List<Group> groups = groupMapper.selectBatchIds(groupIds);
//
//		// 按 groupIds 顺序重排
//		Map<Long, Group> id2Group = groups.stream()
//				.collect(Collectors.toMap(Group::getId, g -> g, (a, b) -> a));
//
//		List<GroupQueryResp> records = groupIds.stream()
//				.map(id2Group::get)
//				.filter(Objects::nonNull)
//				.map(g -> BeanUtil.copyProperties(g, GroupQueryResp.class))
//				.toList();

	}

	@Override
	public PageResp<GetMyGroupQuotasResp> getMyGroupQuotas() {
		return null;
	}

	@Override
	public void UpdateMemberQuotas(Long groupId, List<Long> targetUserIds, Integer newLimit) {

	}
}
