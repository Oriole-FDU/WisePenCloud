package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.VerificationResultDTO;
import com.oriole.wisepen.user.api.domain.dto.req.UserInfoUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.UserProfileUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.UserDetailInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.res.UserSearchResponse;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import com.oriole.wisepen.user.service.ISearchUserService;
import com.oriole.wisepen.user.service.IUserService;
import com.oriole.wisepen.user.strategy.VerificationStrategyFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "用户资料", description = "用户资料查询、更新与身份验证")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;
    private final ISearchUserService searchUserService;
    private final VerificationStrategyFactory verificationStrategyFactory;

    @Operation(
            summary = "搜索用户",
            description = """
                    - 用途：为登录用户提供按关键词查找其他用户的展示信息能力。
                    - 请求：keyword 是可选搜索关键词；传入时 nickname、realName 使用模糊匹配，username、campusNo 使用精确匹配；groupIds 为空时使用当前用户已加入的小组范围，非空时限定在这些小组成员内；page 和 size 控制分页。
                    - 约束：当前用户必须已登录；传入 groupIds 时，当前用户必须属于其中每一个小组。
                    - 处理：查询状态正常的用户账号展示字段，不返回手机号、邮箱、账号状态或密码等敏感信息；keyword 为空时返回小组范围内全部成员；按小组搜索时先校验请求者的小组成员身份，再按小组成员范围过滤。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不属于任一请求小组 -> PermissionError.PERMISSION_DENIED。
                    - 响应：返回匹配用户的分页展示信息列表。
                    """
    )
    @CheckLogin
    @GetMapping("/search")
    @Log(title = "搜索用户", businessType = BusinessType.SELECT, isSaveResponseData = false)
    public R<PageR<UserSearchResponse>> searchUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "groupIds", required = false) List<Long> groupIds,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        List<Long> searchGroupIds = groupIds;
        if (groupIds != null && !groupIds.isEmpty()) {
            groupIds.forEach(SecurityContextHolder::assertInGroup);
        } else {
            searchGroupIds = List.copyOf(SecurityContextHolder.getGroupRoleMap().keySet());
        }
        return R.ok(searchUserService.searchUsers(keyword, searchGroupIds, page, size));
    }

    @Operation(
            summary = "获取当前用户信息",
            description = """
                    - 用途：查询当前登录用户的账号信息、资料信息和可编辑字段边界。
                    - 请求：无需显式传入 userId，目标用户来自当前认证上下文。
                    - 约束：当前用户必须已登录。
                    - 处理：读取 sys_user 与 sys_user_profile，并在用户已完成身份认证时根据认证方式计算只读字段；不刷新认证状态。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：返回用户账号信息、资料信息和只读字段列表。
                    """
    )
    @CheckLogin
    @GetMapping("/getUserInfo")
    @Log(title = "用户信息获取", businessType= BusinessType.SELECT, isSaveResponseData=false)
    public R<UserDetailInfoResponse> getUserInfo() {
        Long userId = SecurityContextHolder.getUserId();
        UserDetailInfoResponse userInfo = userService.getUserInfoById(userId);
        return R.ok(userInfo);
    }

    @Operation(
            summary = "更新用户资料",
            description = """
                    - 用途：当前用户维护自己的学籍、院系、专业、班级、入学年份、学历层次、职称与性别资料。
                    - 请求：请求体字段属于用户资料域，对应 sys_user_profile；空字段不覆盖已有值。
                    - 约束：当前用户必须已登录且已完成身份认证；认证策略标记的只读字段不能被用户修改。
                    - 处理：更新当前用户资料域；学生身份会清空职称字段，非学生身份会清空专业和班级字段；不修改账号、密码、状态或认证方式。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；用户未完成身份认证 -> UserError.CANNOT_OPERATE_BEFORE_AUTH_VERIFICATION。
                    - 响应：成功时返回空结果。
                    """
    )
    @CheckLogin
    @PutMapping("/changeUserProfile")
    @Log(title = "更新用户资料", businessType = BusinessType.UPDATE)
    public R<Void> updateUserProfile(@RequestBody UserProfileUpdateRequest dto) {
        userService.updateProfile(SecurityContextHolder.getUserId(), dto);
        return R.ok();
    }

    @Operation(
            summary = "更新用户信息",
            description = """
                    - 用途：当前用户维护自己的展示类账号信息。
                    - 请求：nickname、avatar、realName 为可更新的账号展示字段。
                    - 约束：当前用户必须已登录。
                    - 处理：更新当前用户 sys_user 中的展示信息，不修改用户名、学工号、身份类型、认证方式、账号状态或密码。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：成功时返回空结果。
                    """
    )
    @CheckLogin
    @PutMapping("/changeUserInfo")
    @Log(title = "更新用户信息", businessType = BusinessType.UPDATE)
    public R<Void> updateUserInfo(@RequestBody UserInfoUpdateRequest dto) {
        userService.updateUserInfo(SecurityContextHolder.getUserId(), dto);
        return R.ok();
    }

    @Operation(
            summary = "发起邮箱验证",
            description = """
                    - 用途：当前用户通过教育邮箱发起身份认证流程。
                    - 请求：email 为待验证的邮箱地址。
                    - 约束：当前用户必须已登录；邮箱需满足对应认证策略的业务校验。
                    - 处理：将 email 和当前 userId 交给教育邮箱认证策略发起验证；不立即改变用户认证状态。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；邮箱不符合认证策略 -> UserError.VERIFICATION_EMAIL_INVALID；邮箱已被其他账号绑定 -> UserError.VERIFICATION_EMAIL_ALREADY_EXISTS；验证邮件发送失败 -> UserError.VERIFICATION_EMAIL_SEND_FAILED。
                    - 响应：成功受理时返回空结果，后续结果通过邮箱回调检查接口完成。
                    """
    )
    @CheckLogin
    @PostMapping("/verify/initiateEmailVerify")
    @Log(title = "发起邮箱验证", businessType = BusinessType.OTHER)
    public R<Void> initiateEmailVerify(@RequestParam("email") String email) {
        Map<String,Object> map = new HashMap<>();
        map.put("email", email);
        verificationStrategyFactory.getStrategy(UserVerificationMode.EDU_EMAIL)
                .initiate(SecurityContextHolder.getUserId(), map);
        return R.ok();
    }

    @Operation(
            summary = "检查邮箱验证回调",
            description = """
                    - 用途：邮箱验证链接回调后校验一次性 token 并完成教育邮箱认证。
                    - 请求：token 为邮箱验证流程生成的一次性凭证。
                    - 约束：token 必须有效且未过期。
                    - 处理：调用教育邮箱认证策略校验 token，并按策略更新用户认证相关信息；不依赖当前登录用户上下文。
                    - 失败：验证 token 无效或过期 -> UserError.VERIFICATION_EMAIL_TOKEN_EXPIRED；邮箱已被其他账号绑定 -> UserError.VERIFICATION_EMAIL_ALREADY_EXISTS。
                    - 响应：成功时返回空结果。
                    """
    )
    @GetMapping("/verify/checkEmailVerify")
    @Log(title = "邮箱验证回调", businessType = BusinessType.OTHER)
    public R<Void> checkEmailVerify(@RequestParam("token") String token) {
        Map<String,Object> map = new HashMap<>();
        map.put("token", token);
        verificationStrategyFactory.getStrategy(UserVerificationMode.EDU_EMAIL).verify(map);
        return R.ok();
    }

    @Operation(
            summary = "发起复旦 UIS 认证",
            description = """
                    - 用途：当前用户通过复旦 UIS 账号密码发起校内身份认证流程。
                    - 请求：uisAccount 为 UIS 账号；uisPassword 为 UIS 密码。
                    - 约束：当前用户必须已登录；UIS 凭据需通过复旦扩展认证策略校验。
                    - 处理：将 UIS 凭据和当前 userId 交给复旦 UIS 认证策略发起认证任务；不在本接口直接返回最终认证资料。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；UIS 认证请求发送失败 -> UserError.VERIFICATION_FUDAN_UIS_REQUEST_FAILED。
                    - 响应：成功受理时返回空结果，认证进度通过状态检查接口查询。
                    """
    )
    @CheckLogin
    @PostMapping("/verify/initiateFudanUISVerify")
    @Log(title = "发起复旦UIS认证", businessType = BusinessType.OTHER)
    public R<Void> initiateFudanUISVerify(@RequestParam("uisAccount") String uisAccount, @RequestParam("uisPassword") String uisPassword) {
        Map<String,Object> map = new HashMap<>();
        map.put("uisAccount", uisAccount);
        map.put("uisPassword", uisPassword);
        verificationStrategyFactory.getStrategy(UserVerificationMode.FDU_UIS_SYS)
                .initiate(SecurityContextHolder.getUserId(), map);
        return R.ok();
    }

    @Operation(
            summary = "检查复旦 UIS 认证状态",
            description = """
                    - 用途：查询当前用户复旦 UIS 认证任务的处理结果。
                    - 请求：无需请求参数，目标用户来自当前认证上下文。
                    - 约束：当前用户必须已登录，并且已发起过复旦 UIS 认证流程。
                    - 处理：调用复旦 UIS 认证策略校验当前用户任务状态，并在认证成功时推进用户认证结果。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；UIS 任务不存在或已过期 -> FudanExtensionError.UIS_TASK_NOT_FOUND；UIS 认证失败或仍未完成 -> UserError.VERIFICATION_FUDAN_UIS_FAILED；学工号已被其他账号绑定 -> UserError.VERIFICATION_CAMPUS_NO_ALREADY_EXISTS。
                    - 响应：返回认证结果信息。
                    """
    )
    @CheckLogin
    @GetMapping("/verify/checkFudanUISVerify")
    @Log(title = "检查复旦UIS认证状态", businessType = BusinessType.OTHER)
    public R<VerificationResultDTO> checkFudanUISVerify() {
        Map<String,Object> map = new HashMap<>();
        map.put("userId", SecurityContextHolder.getUserId());
        VerificationResultDTO dto = verificationStrategyFactory.getStrategy(UserVerificationMode.FDU_UIS_SYS).verify(map);
        return R.ok(dto);
    }
}
