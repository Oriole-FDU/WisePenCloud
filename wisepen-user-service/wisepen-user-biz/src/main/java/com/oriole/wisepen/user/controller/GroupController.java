package com.oriole.wisepen.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.user.api.domain.dto.*;
import com.oriole.wisepen.user.domain.entity.Group;
import com.oriole.wisepen.user.service.GroupMemberService;
import com.oriole.wisepen.user.service.GroupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/group")
@RequiredArgsConstructor
@Validated
public class GroupController {

	private final GroupService groupService;
	private final GroupMemberService groupMemberService;

	@SaCheckLogin
	@PostMapping("/new")
	public R<?> createGroup(@RequestBody @Valid CreateGroupReq req) {

		Group group = new Group();
		group.setName(req.getGroupName());
		group.setType(req.getGroupType());
		group.setDescription(req.getDescription());
		group.setCoverUrl(req.getCoverUrl());

		group.setOwnerId(SecurityContextHolder.getUserId());
		groupService.createGroup(group);

		Long userId = SecurityContextHolder.getUserId();
		groupMemberService.becomeGroupOwner(userId, group.getId());

		return R.ok();
	}

	@SaCheckLogin
	@PostMapping("/edit")
	public R<?> updateGroup(@RequestBody @Valid UpdateGroupReq req) {
		Group group = new Group();
		group.setId(req.getGroupId());
		group.setName(req.getGroupName());
		group.setDescription(req.getDescription());
		group.setCoverUrl(req.getCoverUrl());
		groupService.updateGroup(group);
		return R.ok();
	}

	@SaCheckLogin
	@PostMapping("/delete")
	public R<?> deleteGroup(@RequestBody @Valid DeleteGroupReq req) {
		groupService.deleteGroup(req.getGroupId());
		return R.ok();
	}

	// GET 不动
	@SaCheckLogin
	@GetMapping("/info")
	public R<PageResp<GroupQueryResp>> getInfo(
			@RequestParam("relationType") @NonNull Integer relationType,
			@RequestParam("page") @NonNull @Min(1) Integer page,
			@RequestParam("size") @NonNull @Min(1) Integer size
	) {
		Long userId = SecurityContextHolder.getUserId();
		return R.ok(groupService.getGroupIds(userId, relationType, page, size));
	}
}
