package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberKickRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberRoleUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberQuitRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberTokenLimitUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberDetailResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberTokenDetailResponse;
import com.oriole.wisepen.user.service.IGroupMemberService;
import com.oriole.wisepen.user.service.IWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "小组成员", description = "小组成员退出、移除、角色、配额与列表查询")
@RestController
@RequestMapping("/group/member")
@RequiredArgsConstructor
@Validated
@CheckRole
public class GroupMemberController {

	private final IGroupMemberService groupMemberService;
	private final IWalletService walletService;

	@Operation(
			summary = "退出小组",
			description = """
					- 用途：当前用户主动退出自己所在的小组。
					- 请求：groupId 指定要退出的小组。
					- 约束：当前用户必须已登录且属于目标小组；OWNER 不能通过本接口退出小组。
					- 处理：删除当前用户的小组成员关系，并刷新会话中的小组角色缓存为 NOT_MEMBER。
					- 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不在小组中 -> PermissionError.PERMISSION_DENIED；当前用户是 OWNER -> UserError.CANNOT_QUIT_GROUP_AS_OWNER。
					- 响应：成功时返回空结果。
					"""
	)
	@PostMapping("/quit")
	public R<Void> quitGroup(@RequestBody @Valid GroupMemberQuitRequest req) {
		SecurityContextHolder.assertInGroup(req.getGroupId()); // 用户退群必须先在群中
		groupMemberService.quitGroup(req, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRole(req.getGroupId()));
		return R.ok();
	}

	@Operation(
			summary = "移除小组成员",
			description = """
					- 用途：小组管理员从小组中移除一个或多个成员。
					- 请求：groupId 指定目标小组；targetUserIds 指定待移除成员。
					- 约束：当前用户必须是目标小组 OWNER 或 ADMIN；不能移除自己；操作者角色权限必须高于被移除成员。
					- 处理：删除符合权限条件的成员关系，并将被移除成员会话中的该小组角色刷新为 NOT_MEMBER。
					- 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是 OWNER/ADMIN -> PermissionError.PERMISSION_DENIED；目标成员不存在或没有任何成员可被移除 -> UserError.GROUP_MEMBER_NOT_FOUND。
					- 响应：成功时返回空结果。
					"""
	)
	@PostMapping("/kick")
	public R<Void> kickGroupMembers(@RequestBody @Valid GroupMemberKickRequest req) {
		SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER, GroupRoleType.ADMIN);
		groupMemberService.kickGroupMembers(req, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRole(req.getGroupId()));
		return R.ok();
	}

	@Operation(
			summary = "更新成员角色",
			description = """
					- 用途：小组 OWNER 批量调整成员在小组内的角色。
					- 请求：groupId 指定目标小组；targetUserIds 指定成员列表；role 指定新角色。
					- 约束：当前用户必须是目标小组 OWNER；不能修改自己的角色；目标成员必须属于该小组。
					- 处理：批量更新目标成员角色，并刷新目标成员会话中的小组角色缓存；不修改小组 OWNER 自身角色。
					- 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是 OWNER -> PermissionError.PERMISSION_DENIED；目标成员不存在或请求只包含操作者自己 -> UserError.GROUP_MEMBER_NOT_FOUND。
					- 响应：成功时返回空结果。
					"""
	)
	@PostMapping("/changeRole")
	public R<Void> changeRole(@RequestBody @Valid GroupMemberRoleUpdateRequest req) {
		SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER); //必须是所有者才能修改成员权限
		groupMemberService.updateGroupMemberRole(req, SecurityContextHolder.getUserId());
		return R.ok();
	}

	@Operation(
			summary = "分页查询小组成员",
			description = """
					- 用途：查询指定小组的成员列表。
					- 请求：groupId 指定目标小组；page 和 size 控制分页。
					- 约束：当前用户必须已登录且属于目标小组。
					- 处理：按角色升序、加入时间降序分页读取成员关系，并补充成员展示信息。
					- 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不属于目标小组 -> PermissionError.PERMISSION_DENIED。
					- 响应：返回分页成员列表和总数。
					"""
	)
	@GetMapping("/list")
	public R<PageR<GroupMemberDetailResponse>> listGroupMembers(
			@RequestParam("groupId") Long groupId,
			@RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
			@RequestParam(value = "size", defaultValue = "20") @Min(1) int size
	) {
		SecurityContextHolder.assertInGroup(groupId);
		return R.ok(groupMemberService.getGroupMemberList(groupId, page, size));
	}

	@Operation(
			summary = "获取我的小组成员信息",
			description = """
					- 用途：查询当前用户在指定小组中的成员记录。
					- 请求：groupId 指定目标小组。
					- 约束：当前用户必须已登录；当前用户必须是目标小组成员。
					- 处理：读取当前用户在目标小组的成员关系，并补充用户展示信息；不返回其他成员信息。
					- 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是目标小组成员 -> UserError.GROUP_MEMBER_NOT_FOUND。
					- 响应：返回当前用户的小组成员详情。
					"""
	)
	@GetMapping("/getMyGroupMemberInfo")
	public R<GroupMemberDetailResponse> getMyGroupMemberInfo(@RequestParam("groupId") Long groupId) {
		return R.ok(groupMemberService.getGroupMemberInfoByUserId(groupId, SecurityContextHolder.getUserId()));
	}

	@Operation(
			summary = "获取我的小组角色",
			description = """
					- 用途：查询当前用户在指定小组中的角色。
					- 请求：groupId 指定目标小组。
					- 约束：当前用户必须已登录，且认证上下文中必须包含该小组角色。
					- 处理：从当前会话的小组角色上下文读取角色，不访问成员表刷新数据。
					- 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不属于目标小组或角色上下文缺失 -> CommonError.SECURITY_CONTEXT_PARAM_MISSING。
					- 响应：返回当前用户的小组角色枚举。
					"""
	)
	@GetMapping("/getMyRole")
	public R<GroupRoleType> getMyRole(@RequestParam("groupId") Long groupId) {
		return R.ok(SecurityContextHolder.getGroupRole(groupId));
	}

	@Operation(
			summary = "更新成员信息点额度",
			description = """
					- 用途：小组 OWNER 调整一个或多个成员在高级小组内的信息点使用额度。
					- 请求：groupId 指定目标小组；targetUserIds 指定成员列表；newTokenLimit 指定新的额度上限。
					- 约束：当前用户必须是目标小组 OWNER；目标小组必须支持配置成员额度；新额度不能低于成员已使用额度。
					- 处理：批量更新成员额度，并对额度高于已使用量的成员解除聊天熔断；不修改小组总余额。
					- 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是 OWNER -> PermissionError.PERMISSION_DENIED；小组不存在 -> UserError.GROUP_NOT_EXIST；普通小组不允许配置额度 -> UserError.CANNOT_CONFIGURE_GROUP_WALLET_QUOTA；额度低于已使用量 -> UserError.WALLET_TOKEN_LIMIT_BELOW_USED。
					- 响应：成功时返回空结果。
					"""
	)
	@PostMapping("/changeTokenLimit")
	public R<Void> changeTokenLimit(@RequestBody @Valid GroupMemberTokenLimitUpdateRequest req) {
		SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER);
		walletService.updateGroupMemberTokenLimit(req);
		return R.ok();
	}

	@Operation(
			summary = "分页查询我的小组信息点额度",
			description = """
					- 用途：查询当前用户在各小组中的信息点额度和使用情况。
					- 请求：page 和 size 控制分页。
					- 约束：当前用户必须已登录。
					- 处理：分页读取当前用户的小组成员记录，并补充对应小组展示信息；不计算实时消费中的未落库用量。
					- 失败：未登录 -> PermissionError.NOT_LOGIN。
					- 响应：返回分页的小组额度明细和总数。
					"""
	)
	@GetMapping("/getAllMyGroupTokenInfo")
	public R<PageR<GroupMemberTokenDetailResponse>> getAllMyGroupTokenInfo(
			@RequestParam(value = "page", defaultValue = "1") @Min(1) Integer page,
			@RequestParam(value = "size", defaultValue = "20") @Min(1) Integer size
	){
		Long userId = SecurityContextHolder.getUserId();
		return R.ok(walletService.getAllGroupTokenInfoByUserId(userId, page, size));
	}
}
