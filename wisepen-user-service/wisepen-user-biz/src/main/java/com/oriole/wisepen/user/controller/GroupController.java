package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.common.security.exception.PermissionError;
import com.oriole.wisepen.common.security.exception.PermissionException;
import com.oriole.wisepen.user.api.domain.dto.req.GroupCreateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupDeleteRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberJoinRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupDetailInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupItemInfoResponse;
import com.oriole.wisepen.user.api.enums.GroupRoleFilter;
import com.oriole.wisepen.user.service.IGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "小组", description = "小组加入、创建、编辑、解散与查询")
@RestController
@RequestMapping("/group")
@RequiredArgsConstructor
@Validated
@CheckRole
public class GroupController {

	private final IGroupService groupService;

	@Operation(
			summary = "加入小组",
			description = """
					- 用途：当前用户通过邀请码加入一个已有小组。
					- 请求：inviteCode 指定目标小组的邀请码。
					- 约束：当前用户必须已登录；邀请码必须对应存在的小组；当前用户不能已经属于该小组。
					- 处理：将当前用户加入目标小组并设置为 MEMBER，同时刷新会话中的小组角色缓存。
					- 失败：未登录 -> PermissionError.NOT_LOGIN；小组不存在 -> UserError.GROUP_NOT_EXIST；用户已在小组中 -> UserError.GROUP_MEMBER_ALREADY_EXISTS。
					- 响应：成功时返回空结果。
					"""
	)
	@PostMapping("/joinGroup")
	public R<Void> joinGroup(@RequestBody @Valid GroupMemberJoinRequest req) {
		groupService.joinGroup(req, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRoleMap().keySet());
		// SecurityContextHolder.getGroupRoleMap().keySet()防止用户重复加群
		return R.ok();
	}

	@Operation(
			summary = "创建小组",
			description = """
					- 用途：当前用户创建新的协作小组并成为小组 OWNER。
					- 请求：请求体提供小组名称、类型等创建信息。
					- 约束：当前用户必须已登录；学生不能创建高级小组；非管理员不能创建市场小组。
					- 处理：创建小组主记录、生成邀请码、初始化余额字段，将当前用户加入为 OWNER，并按小组状态更新聊天熔断缓存。
					- 失败：未登录 -> PermissionError.NOT_LOGIN；身份不允许创建目标小组类型 -> PermissionError.UNAUTHORIZED。
					- 响应：返回新建小组 ID。
					"""
	)
	@PostMapping("/addGroup")
	public R<Long> createGroup(@RequestBody @Valid GroupCreateRequest req) {

		IdentityType userIdentityType= SecurityContextHolder.getIdentityType();
		if (req.getGroupType() == GroupType.ADVANCED_GROUP && userIdentityType == IdentityType.STUDENT) {
			throw new PermissionException(PermissionError.UNAUTHORIZED);
		}

		if (req.getGroupType() == GroupType.MARKET_GROUP && userIdentityType != IdentityType.ADMIN) {
			throw new PermissionException(PermissionError.UNAUTHORIZED);
		}

		if (req.getGroupType() == GroupType.NORMAL_GROUP) {
			req.setGroupMetaInfo(null); // 普通组不能有 Meta 信息
		}

		return R.ok(groupService.createGroup(req, SecurityContextHolder.getUserId()));
	}

	@Operation(
			summary = "更新小组信息",
			description = """
					- 用途：小组 OWNER 维护小组基础信息和小组类型。
					- 请求：groupId 指定目标小组，其余字段为待更新的小组信息。
					- 约束：当前用户必须已登录且是目标小组 OWNER；学生不能将小组设为高级小组；非管理员不能将小组设为市场小组。
					- 处理：更新小组主记录的可维护字段和更新时间；不修改成员列表、邀请码、钱包余额或资源配置。
					- 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是 OWNER -> PermissionError.PERMISSION_DENIED；目标小组不存在 -> UserError.GROUP_NOT_EXIST；目标类型不允许 -> PermissionError.UNAUTHORIZED。
					- 响应：成功时返回空结果。
					"""
	)
	@PostMapping("/changeGroup")
	public R<Void> updateGroup(@RequestBody @Valid GroupUpdateRequest req) {
		SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER);

		IdentityType userIdentityType= SecurityContextHolder.getIdentityType();
		if (req.getGroupType() == GroupType.ADVANCED_GROUP && userIdentityType == IdentityType.STUDENT) {
			throw new PermissionException(PermissionError.UNAUTHORIZED);
		}

		if (req.getGroupType()==GroupType.MARKET_GROUP && userIdentityType != IdentityType.ADMIN) {
			throw new PermissionException(PermissionError.UNAUTHORIZED);
		}

		if (req.getGroupType() == GroupType.NORMAL_GROUP) {
			req.setGroupMetaInfo(null); // 普通组不能有 Meta 信息
		}

		groupService.updateGroup(req);
		return R.ok();
	}

	@Operation(
			summary = "解散小组",
			description = """
					- 用途：小组 OWNER 解散目标小组。
					- 请求：groupId 指定要解散的小组。
					- 约束：当前用户必须已登录且是目标小组 OWNER；目标小组必须存在。
					- 处理：删除小组主记录并移除全部小组成员；高级小组会先将剩余信息点转回操作者；随后通知资源服务清理该小组标签树和资源配置。
					- 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是 OWNER -> PermissionError.PERMISSION_DENIED；目标小组不存在 -> UserError.GROUP_NOT_EXIST；高级小组余额转回失败 -> UserError.WALLET_TOKEN_LIMIT_BELOW_USED；资源服务通知失败会记录日志但不阻断本接口成功返回。
					- 响应：成功时返回空结果。
					"""
	)
	@PostMapping("/removeGroup")
	public R<Void> deleteGroup(@RequestBody @Valid GroupDeleteRequest req) {
		SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER);
		Long userId=SecurityContextHolder.getUserId();
		groupService.deleteGroup(userId, req);
		return R.ok();
	}

	@Operation(
			summary = "分页查询小组列表",
			description = """
					- 用途：查询当前用户加入、管理或全部所属的小组列表。
					- 请求：groupRoleFilter 指定查询全部小组、成员小组或管理小组；groupType 可选地筛选小组类型；page 和 size 控制分页。
					- 约束：当前用户必须已登录。
					- 处理：按当前用户在小组中的角色过滤成员关系，再按可选类型分页读取小组记录并补充小组 OWNER 展示信息。
					- 失败：未登录 -> PermissionError.NOT_LOGIN。
					- 响应：返回分页小组列表和总数。
					"""
	)
	@GetMapping("/list")
	public R<PageR<GroupItemInfoResponse>> listGroups(
			@RequestParam GroupRoleFilter groupRoleFilter,
			@RequestParam(value = "groupType", required = false) GroupType groupType,
			@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "size", defaultValue = "20") int size
	) {
		return R.ok(groupService.getGroupList(SecurityContextHolder.getUserId(), groupRoleFilter, groupType, page, size));
	}

	@Operation(
			summary = "获取小组基础信息",
			description = """
					- 用途：查询指定小组的基础展示信息。
					- 请求：groupId 指定目标小组。
					- 约束：当前用户必须已登录；目标小组必须存在。
					- 处理：读取小组主记录并补充 OWNER 展示信息；不校验当前用户是否属于该小组。
					- 失败：未登录 -> PermissionError.NOT_LOGIN；目标小组不存在 -> UserError.GROUP_NOT_EXIST。
					- 响应：返回小组基础信息。
					"""
	)
	@GetMapping("/getGroupBaseInfo")
	public R<GroupItemInfoResponse> getGroupBaseInfo(@RequestParam("groupId") Long groupId) {
		return R.ok(groupService.getGroupBaseInfoById(groupId));
	}

	@Operation(
			summary = "获取小组详细信息",
			description = """
					- 用途：查询小组管理视角下的详细信息。
					- 请求：groupId 指定目标小组。
					- 约束：当前用户必须已登录，且是目标小组 OWNER 或 ADMIN。
					- 处理：读取小组主记录并补充 OWNER 展示信息；不返回成员分页列表。
					- 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是小组 OWNER/ADMIN -> PermissionError.PERMISSION_DENIED；目标小组不存在 -> UserError.GROUP_NOT_EXIST。
					- 响应：返回小组详细信息。
					"""
	)
	@GetMapping("/getGroupDetailInfo")
	public R<GroupDetailInfoResponse> getGroupDetailInfo(@RequestParam("groupId") Long groupId) {
		SecurityContextHolder.assertGroupRole(groupId, GroupRoleType.OWNER, GroupRoleType.ADMIN);
		return R.ok(groupService.getGroupDetailInfoById(groupId));
	}
}
