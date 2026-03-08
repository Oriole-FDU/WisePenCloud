package com.oriole.wisepen.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.user.api.domain.dto.*;
import com.oriole.wisepen.user.service.GroupMemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/group/member")
@RequiredArgsConstructor
@Validated
public class GroupMemberController {

	private final GroupMemberService groupMemberService;

	@SaCheckLogin
	@PostMapping("/join")
	public R<Void> joinGroup(@RequestBody @Valid JoinGroupReq req) {
		Long userId = SecurityContextHolder.getUserId();
		groupMemberService.joinGroup(userId, req.getInviteCode());
		return R.ok();
	}

	@SaCheckLogin
	@PostMapping("/quit")
	public R<Void> quitGroup(@RequestBody @Valid QuitGroupReq req) {
		Long userId = SecurityContextHolder.getUserId();
		groupMemberService.leaveGroup(userId, req.getGroupId());
		return R.ok();
	}

	@SaCheckLogin
	@PostMapping("/kick")
	public R<Void> kickGroup(@RequestBody @Valid KickGroupReq req) {
		Long userId = SecurityContextHolder.getUserId();
		groupMemberService.kickGroupMembers(userId, req.getGroupId(), req.getTargetUserIds());
		return R.ok();
	}

	@SaCheckLogin
	@PostMapping("/update-role")
	public R<Void> updateRole(@RequestBody @Valid UpdateRoleReq req) {
		groupMemberService.updateGroupMemberRole(req.getGroupId(), req.getTargetUserId(), req.getRole());
		return R.ok();
	}


	@SaCheckLogin
	@GetMapping("/info")
	public R<PageResp<MemberListQueryResp>> getGroupMember(
			@RequestParam("groupId") @NotNull Long groupId,
			@RequestParam("page") @NonNull @Min(1) Integer page,
			@RequestParam("size") @NonNull @Min(1) Integer size
	){
		return R.ok(groupMemberService.getMemberList(groupId,page,size));
	}
}
