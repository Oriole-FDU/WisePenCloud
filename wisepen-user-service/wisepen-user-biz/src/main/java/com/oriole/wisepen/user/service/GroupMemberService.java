package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.api.domain.dto.MemberListQueryResp;
import com.oriole.wisepen.user.api.domain.dto.PageResp;

import java.util.List;

public interface GroupMemberService {

	void joinGroup(Long userId, String inviteCode);
	void leaveGroup(Long userId, Long groupId);
	void kickGroupMember(Long userId, Long groupId, Long targetUserId);

	public void kickGroupMembers(Long operatorUserId, Long groupId, List<Long> targetUserIds);

	PageResp<MemberListQueryResp> getMemberList(Long groupId, Integer page, Integer size);

	void updateGroupMemberRole(Long groupId, Long targetUserId, Integer role);

	void becomeGroupOwner(Long userId, Long groupId);
}
