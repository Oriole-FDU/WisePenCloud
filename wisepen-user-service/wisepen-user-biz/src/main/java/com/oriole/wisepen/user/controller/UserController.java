package com.oriole.wisepen.user.controller;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.campus.extension.api.domain.dto.CampusIdentityProviderDescriptorDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.CampusIdentityTaskResultDTO;
import com.oriole.wisepen.campus.extension.api.enums.CampusIdentityTaskState;
import com.oriole.wisepen.campus.extension.api.enums.LoginChallengeType;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.dto.req.UserInfoUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.CampusIdentityLoginChallengeSubmitRequest;
import com.oriole.wisepen.user.api.domain.dto.req.CampusIdentityVerifyInitiateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.UserDetailInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.res.UserSearchUserResponse;
import com.oriole.wisepen.user.api.domain.dto.VerificationResultDTO;
import com.oriole.wisepen.user.api.domain.dto.req.UserProfileUpdateRequest;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.service.IUserService;
import com.oriole.wisepen.user.strategy.impl.CampusIdentityVerificationStrategy;
import com.oriole.wisepen.user.strategy.impl.EmailVerificationStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Tag(name = "用户资料", description = "用户资料查询、更新与身份验证")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final IUserService userService;

    private final EmailVerificationStrategy emailVerificationStrategy;
    private final CampusIdentityVerificationStrategy campusIdentityVerificationStrategy;

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
            summary = "搜索用户",
            description = """
                    - 用途：在站内消息等需要选择接收人的场景中，根据完整用户名或邮箱查找可见用户。
                    - 请求：keyword 为完整用户名或完整邮箱；不接受学工号搜索，也不做模糊匹配。
                    - 约束：当前用户必须已登录；可见用户必须账号正常且完成身份认证。
                    - 处理：精确查询 sys_user 的 username 或 email，并过滤未认证或非正常状态用户；不返回邮箱、手机号或学工号。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：返回符合条件的用户展示列表；无可见用户时返回空列表。
                    """
    )
    @CheckRole
    @GetMapping("/searchUser")
    public R<List<UserSearchUserResponse>> searchUser(
            @RequestParam("keyword") @NotBlank @Size(max = 128) String keyword
    ) {
        return R.ok(userService.searchUser(keyword));
    }

    @Operation(
            summary = "查询用户搜索补全",
            description = """
                    - 用途：在输入接收人时，从当前用户所在小组范围内提供候选用户补全。
                    - 请求：keyword 为补全前缀，至少 2 个字符；size 控制返回数量，默认 10，最大 20。
                    - 约束：当前用户必须已登录；候选用户必须与当前用户至少同属一个小组、账号正常且完成身份认证。
                    - 处理：从当前认证上下文读取用户所在小组，批量读取这些小组的成员，再按 username、email、campusNo 前缀匹配；不返回邮箱、手机号或学工号。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：返回包含真实姓名的用户展示补全列表；无可见候选用户时返回空列表。
                    """
    )
    @CheckRole
    @GetMapping("/listUserSearchSuggestions")
    public R<List<UserSearchUserResponse>> listUserSearchSuggestions(
            @RequestParam("keyword") @NotBlank @Size(min = 2, max = 128) String keyword,
            @RequestParam(value = "size", defaultValue = "10") @Min(1) @Max(20) Integer size
    ) {
        return R.ok(userService.listUserSearchSuggestions(SecurityContextHolder.getGroupRoleMap().keySet(), keyword, size));
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
    @CheckRole
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
    @CheckRole
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
                    - 约束：当前用户必须已登录且仍处于未完成身份认证状态；邮箱需满足对应认证策略的业务校验。
                    - 处理：将 email 和当前 userId 交给教育邮箱认证策略发起验证；不立即改变用户认证状态。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；邮箱不符合认证策略 -> UserError.VERIFICATION_EMAIL_INVALID；合作高校不能通过教育邮箱认证 -> UserError.VERIFICATION_EMAIL_NOT_ALLOWED；账号状态不允许邮箱认证 -> UserError.VERIFICATION_EMAIL_STATE_INVALID；邮箱已被其他账号绑定 -> UserError.VERIFICATION_EMAIL_ALREADY_EXISTS；验证邮件发送失败 -> UserError.VERIFICATION_EMAIL_SEND_FAILED。
                    - 响应：成功受理时返回空结果，后续结果通过邮箱回调检查接口完成。
                    """
    )
    @CheckLogin
    @PostMapping("/verify/initiateEmailVerify")
    @Log(title = "发起邮箱验证", businessType = BusinessType.OTHER)
    public R<Void> initiateEmailVerify(@RequestParam("email") String email) {
        email = StrUtil.blankToDefault(email, "").trim().toLowerCase(Locale.ROOT);
        emailVerificationStrategy.initiateEmailVerification(SecurityContextHolder.getUserId(), email);
        return R.ok();
    }

    @Operation(
            summary = "检查邮箱验证回调",
            description = """
                    - 用途：邮箱验证链接回调后校验一次性 token 并完成教育邮箱认证。
                    - 请求：token 为邮箱验证流程生成的一次性凭证。
                    - 约束：token 必须有效且未过期；对应用户仍必须处于未完成身份认证状态。
                    - 处理：调用教育邮箱认证策略校验 token，并按策略更新用户邮箱、认证状态和学校名称；不依赖当前登录用户上下文，不改变学生身份。
                    - 失败：验证 token 无效或过期 -> UserError.VERIFICATION_EMAIL_TOKEN_EXPIRED；邮箱不符合认证策略 -> UserError.VERIFICATION_EMAIL_INVALID；合作高校不能通过教育邮箱认证 -> UserError.VERIFICATION_EMAIL_NOT_ALLOWED；账号状态不允许邮箱认证 -> UserError.VERIFICATION_EMAIL_STATE_INVALID；邮箱已被其他账号绑定 -> UserError.VERIFICATION_EMAIL_ALREADY_EXISTS。
                    - 响应：成功时返回空结果。
                    """
    )
    @GetMapping("/verify/checkEmailVerify")
    @Log(title = "邮箱验证回调", businessType = BusinessType.OTHER)
    public R<Void> checkEmailVerify(@RequestParam("token") String token) {
        emailVerificationStrategy.completeEmailVerification(token);
        return R.ok();
    }

    @Operation(
            summary = "查询校园身份认证 Provider",
            description = """
                    - 用途：查询当前可用的校园身份认证 Provider。
                    - 请求：无需请求参数。
                    - 约束：当前用户必须已登录。
                    - 处理：读取用户服务配置的 Provider，并向扩展服务获取展示描述。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；Provider 不存在或扩展服务不可用 -> UserError.VERIFICATION_CAMPUS_IDENTITY_PROVIDER_NOT_FOUND。
                    - 响应：返回 Provider 展示信息、所属学校和所需输入。
                    """
    )
    @CheckLogin
    @GetMapping("/verify/listCampusIdentityProviders")
    public R<List<CampusIdentityProviderDescriptorDTO>> listCampusIdentityProviders() {
        return R.ok(campusIdentityVerificationStrategy.listProviders());
    }

    @Operation(
            summary = "发起校园身份认证",
            description = """
                    - 用途：当前用户通过指定校园身份认证 Provider 发起校内身份认证流程。
                    - 请求：providerCode 指定 Provider；payload 透传该 Provider 所需输入。
                    - 约束：当前用户必须已登录且仍处于未完成身份认证状态。
                    - 处理：将请求转交校园身份认证扩展服务；不在本接口直接完成用户认证落库。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；账号状态不允许认证 -> UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID；扩展请求失败 -> UserError.VERIFICATION_CAMPUS_IDENTITY_REQUEST_FAILED。
                    - 响应：返回认证任务当前状态；需要扫码或验证码时返回 loginChallenge。
                    """
    )
    @CheckLogin
    @PostMapping("/verify/initiateCampusIdentityVerify")
    @Log(title = "发起校园身份认证", businessType = BusinessType.OTHER, isSaveRequestData = false)
    public R<CampusIdentityTaskResultDTO> initiateCampusIdentityVerify(
            @Validated @RequestBody CampusIdentityVerifyInitiateRequest req) {
        CampusIdentityTaskResultDTO dto = campusIdentityVerificationStrategy.initiateCampusIdentityVerification(
                SecurityContextHolder.getUserId(),
                req.getProviderCode(),
                req.getPayload()
        );
        return R.ok(dto);
    }

    @Operation(
            summary = "提交校园身份认证登录 Challenge",
            description = """
                    - 用途：当前用户在校园身份认证流程中提交验证码、二次验证等 Challenge 响应。
                    - 请求：providerCode 指定 Provider；taskId、challengeId 和 payload 来自当前认证任务。
                    - 约束：当前用户必须已登录且仍处于未完成身份认证状态。
                    - 处理：将 Challenge 响应转交校园身份认证扩展服务。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；账号状态不允许认证 -> UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID；扩展请求失败 -> UserError.VERIFICATION_CAMPUS_IDENTITY_REQUEST_FAILED。
                    - 响应：返回认证任务当前状态。
                    """
    )
    @CheckLogin
    @PostMapping("/verify/submitCampusIdentityLoginChallenge")
    @Log(title = "提交校园身份认证登录Challenge", businessType = BusinessType.OTHER, isSaveRequestData = false)
    public R<CampusIdentityTaskResultDTO> submitCampusIdentityLoginChallenge(
            @Validated @RequestBody CampusIdentityLoginChallengeSubmitRequest req) {
        CampusIdentityTaskResultDTO dto = campusIdentityVerificationStrategy.submitLoginChallenge(
                SecurityContextHolder.getUserId(),
                req.getProviderCode(),
                req.getTaskId(),
                req.getChallengeId(),
                req.getPayload()
        );
        return R.ok(dto);
    }

    @Operation(
            summary = "检查校园身份认证状态",
            description = """
                    - 用途：查询当前用户校园身份认证任务状态，并在成功时完成用户认证落库。
                    - 请求：providerCode 指定 Provider；taskId 指定一次具体认证尝试。
                    - 约束：当前用户必须已登录且仍处于未完成身份认证状态。
                    - 处理：按 taskId 查询扩展服务任务状态；成功时根据标准身份 claims 更新用户资料、认证状态和奖励。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；任务失败或资料缺失 -> UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED；学工号已被其他账号绑定 -> UserError.VERIFICATION_CAMPUS_NO_ALREADY_EXISTS。
                    - 响应：返回认证任务当前状态。
                    """
    )
    @CheckLogin
    @GetMapping("/verify/checkCampusIdentityVerify")
    @Log(title = "检查校园身份认证状态", businessType = BusinessType.OTHER, isSaveResponseData = false)
    public R<CampusIdentityTaskResultDTO> checkCampusIdentityVerify(
            @RequestParam("providerCode") @NotBlank String providerCode,
            @RequestParam("taskId") @NotBlank String taskId) {
        CampusIdentityTaskResultDTO dto = campusIdentityVerificationStrategy.checkCampusIdentity(
                SecurityContextHolder.getUserId(),
                providerCode,
                taskId
        );
        return R.ok(dto);
    }

    @Deprecated
    @Operation(
            summary = "发起复旦 UIS 认证",
            description = """
                    - 用途：兼容旧前端入口，当前用户通过复旦 UIS 账号密码发起校园身份认证流程。
                    - 请求：uisAccount 为 UIS 账号；uisPassword 为 UIS 密码。
                    - 约束：当前用户必须已登录且仍处于未完成身份认证状态。
                    - 处理：将 UIS 凭据映射为 FUDAN_UIS Provider 输入，并复用校园身份认证通用流程；不在本接口直接返回最终认证资料。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；账号状态不允许认证 -> UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID；扩展请求失败 -> UserError.VERIFICATION_CAMPUS_IDENTITY_REQUEST_FAILED。
                    - 响应：成功受理时返回空结果，认证进度通过状态检查接口查询。
                    """
    )
    @CheckLogin
    @PostMapping("/verify/initiateFudanUISVerify")
    @Log(title = "发起复旦UIS认证", businessType = BusinessType.OTHER, isSaveRequestData = false)
    public R<Void> initiateFudanUISVerify(
            @RequestParam("uisAccount") @NotBlank String uisAccount,
            @RequestParam("uisPassword") @NotBlank String uisPassword) {
        Map<String,Object> map = new HashMap<>();
        map.put("account", uisAccount);
        map.put("password", uisPassword);
        campusIdentityVerificationStrategy.initiateCampusIdentityVerification(
                SecurityContextHolder.getUserId(), "FUDAN_UIS", map
        );
        return R.ok();
    }

    @Deprecated
    @Operation(
            summary = "检查复旦 UIS 认证状态",
            description = """
                    - 用途：兼容旧前端入口，查询当前用户复旦 UIS 认证任务的处理结果。
                    - 请求：无需请求参数，目标用户来自当前认证上下文。
                    - 约束：当前用户必须已登录，并且已发起过 FUDAN_UIS 校园身份认证流程。
                    - 处理：复用校园身份认证通用流程查询任务状态，并在认证成功时根据标准身份资料更新用户身份和认证结果。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；账号状态不允许认证 -> UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID；任务失败或资料缺失 -> UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED；学工号已被其他账号绑定 -> UserError.VERIFICATION_CAMPUS_NO_ALREADY_EXISTS。
                    - 响应：返回认证结果信息。
                    """
    )
    @CheckLogin
    @GetMapping("/verify/checkFudanUISVerify")
    @Log(title = "检查复旦UIS认证状态", businessType = BusinessType.OTHER, isSaveResponseData = false)
    public R<VerificationResultDTO> checkFudanUISVerify() {
        CampusIdentityTaskResultDTO dto = campusIdentityVerificationStrategy.checkCampusIdentity(
                SecurityContextHolder.getUserId(), "FUDAN_UIS", null
        );
        return R.ok(toLegacyFudanUISVerificationResult(dto));
    }

    /** 将标准校园身份任务结果转换为旧复旦前端仍使用的 VerificationResultDTO。 */
    private VerificationResultDTO toLegacyFudanUISVerificationResult(CampusIdentityTaskResultDTO taskResult) {
        if (taskResult.getState() == CampusIdentityTaskState.QUEUED
                || taskResult.getState() == CampusIdentityTaskState.PENDING) {
            return VerificationResultDTO.pending();
        }
        if (taskResult.getState() == CampusIdentityTaskState.CHALLENGE_REQUIRED) {
            String actionPayload = null;
            if (taskResult.getChallenge() != null && taskResult.getChallenge().getType() == LoginChallengeType.QR_SCAN) {
                List<String> base64Images = taskResult.getChallenge().getBase64Images();
                if (base64Images != null && !base64Images.isEmpty()) {
                    actionPayload = base64Images.getFirst();
                }
            }
            return VerificationResultDTO.builder()
                    .completed(false)
                    .requireAction(true)
                    .actionPayload(actionPayload)
                    .message(taskResult.getMessage())
                    .build();
        }
        if (taskResult.getState() == CampusIdentityTaskState.SUCCESS) {
            return VerificationResultDTO.success();
        }
        throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED, taskResult.getMessage());
    }
}
