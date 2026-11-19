package com.oriole.wisepen.user.controller;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.campus.extension.api.domain.dto.ClassicAuthTaskResultDTO;
import com.oriole.wisepen.campus.extension.api.enums.ChallengeArtifactEncoding;
import com.oriole.wisepen.campus.extension.api.enums.ClassicAuthTaskState;
import com.oriole.wisepen.campus.extension.api.enums.LoginChallengeType;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.config.UserProperties;
import com.oriole.wisepen.user.api.domain.dto.req.UserInfoUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.CampusIdentityLoginChallengeSubmitRequest;
import com.oriole.wisepen.user.api.domain.dto.req.CampusIdentityVerifyInitiateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.UserDetailInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.res.UserSearchUserResponse;
import com.oriole.wisepen.user.api.domain.dto.VerificationResultDTO;
import com.oriole.wisepen.user.api.domain.dto.req.UserProfileUpdateRequest;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.campus.strategy.CampusClassicAuthFlowStrategy;
import com.oriole.wisepen.user.campus.strategy.CampusOAuth2FlowStrategy;
import com.oriole.wisepen.user.service.IUserService;
import com.oriole.wisepen.user.strategy.verification.UserVerificationCompleteStrategy;
import com.oriole.wisepen.user.strategy.verification.UserVerificationInitStrategy;
import com.oriole.wisepen.user.strategy.verification.UserVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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
    private final UserProperties userProperties;
    private final CampusClassicAuthFlowStrategy campusClassicAuthFlowStrategy;
    private final CampusOAuth2FlowStrategy campusOAuth2FlowStrategy;
    private final UserVerificationService userVerificationService;

    @Operation(
            summary = "获取当前用户信息",
            description = """
                    - 用途：查询当前登录用户的账号信息、资料信息和可编辑字段边界。
                    - 请求：无需显式传入 userId，目标用户来自当前认证上下文。
                    - 约束：当前用户必须已登录。
                    - 处理：读取 user_account 与 user_profile，并在用户已完成身份认证时根据认证方式计算只读字段；不刷新认证状态。
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
                    - 处理：精确查询 user_account 的 username 或 email，并过滤未认证或非正常状态用户；不返回邮箱、手机号或学工号。
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
                    - 请求：请求体字段属于用户资料域，对应 user_profile；空字段不覆盖已有值。
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
                    - 处理：更新当前用户 user_account 中的展示信息，不修改用户名、学工号、身份类型、认证方式、账号状态或密码。
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
        userVerificationService.initVerification(new UserVerificationInitStrategy.EmailInitCommand(SecurityContextHolder.getUserId(), email));
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
        userVerificationService.completeVerification(new UserVerificationCompleteStrategy.EmailCompleteCommand(token));
        return R.ok();
    }

    @Operation(
            summary = "发起校园身份认证（经典模式）",
            description = """
                    - 用途：当前用户通过指定校园身份认证 Provider 发起校内身份认证流程。
                    - 请求：providerCode 指定 Provider；inputs 传递该 Provider 声明的字符串输入。
                    - 约束：当前用户必须已登录且仍处于未完成身份认证状态。
                    - 处理：将请求转交校园身份认证扩展服务；不在本接口直接完成用户认证落库。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；账号状态不允许认证 -> UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID；扩展请求失败 -> UserError.CAMPUS_CLASSIC_AUTH_REQUEST_FAILED。
                    - 响应：返回认证任务当前状态；需要扫码或验证码时返回 loginChallenge。
                    """
    )
    @CheckLogin
    @PostMapping("/verify/initiateCampusClassicIdentityVerify")
    @Log(title = "发起校园身份认证", businessType = BusinessType.OTHER, isSaveRequestData = false)
    public R<ClassicAuthTaskResultDTO> initiateCampusClassicIdentityVerify(
            @Validated @RequestBody CampusIdentityVerifyInitiateRequest req) {
        ClassicAuthTaskResultDTO dto = (ClassicAuthTaskResultDTO) userVerificationService.initVerification(
                new UserVerificationInitStrategy.CampusProviderClassicAuthInitCommand(
                        SecurityContextHolder.getUserId(), req.getProviderCode(), req.getInputs()
                )
        );
        return R.ok(dto);
    }

    @Operation(
            summary = "提交校园登录 Challenge（仅经典模式）",
            description = """
                    - 用途：当前用户在校园身份认证流程中提交验证码、二次验证等 Challenge 响应。
                    - 请求：providerCode 指定 Provider；taskId、challengeId 来自当前认证任务，answers 提交字符串答案。
                    - 约束：当前用户必须已登录且仍处于未完成身份认证状态。
                    - 处理：将 Challenge 响应转交校园身份认证扩展服务。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；账号状态不允许认证 -> UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID；扩展请求失败 -> UserError.CAMPUS_CLASSIC_AUTH_REQUEST_FAILED。
                    - 响应：返回认证任务当前状态。
                    """
    )
    @CheckLogin
    @PostMapping("/verify/submitCampusClassicLoginChallenge")
    @Log(title = "提交校园登录 Challenge （仅经典模式）", businessType = BusinessType.OTHER, isSaveRequestData = false)
    public R<ClassicAuthTaskResultDTO> submitCampusClassicLoginChallenge(
            @Validated @RequestBody CampusIdentityLoginChallengeSubmitRequest req) {
        ClassicAuthTaskResultDTO dto = campusClassicAuthFlowStrategy.submitLoginChallenge(
                SecurityContextHolder.getUserId(),
                req.getProviderCode(),
                req.getTaskId(),
                req.getChallengeId(),
                req.getAnswers()
        );
        return R.ok(dto);
    }

    @Operation(
            summary = "检查校园身份认证状态（仅经典模式）",
            description = """
                    - 用途：查询当前用户校园身份认证任务状态，并在成功时完成用户认证落库。
                    - 请求：providerCode 指定 Provider；taskId 指定一次具体认证尝试。
                    - 约束：当前用户必须已登录且仍处于未完成身份认证状态。
                    - 处理：按 taskId 查询扩展服务任务状态；成功时根据标准身份 claims 更新用户资料、认证状态和奖励。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；账号状态不允许认证 -> UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID；任务失败或资料缺失 -> UserError.CAMPUS_CLASSIC_AUTH_FAILED；学工号已被其他账号绑定 -> UserError.VERIFICATION_CAMPUS_NO_ALREADY_EXISTS。
                    - 响应：返回认证任务当前状态。
                    """
    )
    @CheckLogin
    @GetMapping("/verify/checkCampusClassicIdentityVerify")
    @Log(title = "检查校园身份认证状态（仅经典模式）", businessType = BusinessType.OTHER, isSaveResponseData = false)
    public R<ClassicAuthTaskResultDTO> checkCampusClassicIdentityVerify(
            @RequestParam("providerCode") @NotBlank String providerCode,
            @RequestParam("taskId") @NotBlank String taskId) {
        ClassicAuthTaskResultDTO dto = campusClassicAuthFlowStrategy.checkCampusIdentity(
                SecurityContextHolder.getUserId(),
                providerCode,
                taskId
        );
        return R.ok(dto);
    }

    @Operation(
            summary = "发起校园身份认证 OAuth2.0 授权",
            description = """
                    - 用途：当前用户通过 OAuth2.0 授权发起校园身份认证流程。
                    - 请求：providerCode 指定校园身份认证 Provider。
                    - 约束：当前用户必须已登录；Provider 必须声明 OAuth2.0 身份认证协议且已配置回调地址。
                    - 处理：生成一次性 state（含当前 userId）并缓存，向扩展服务换取授权页地址后 302 跳转；不立即改变用户认证状态。
                    - 失败：Provider 不存在或未声明 OAuth2.0 身份认证协议 -> UserError.AUTH_CAMPUS_OAUTH_PROVIDER_NOT_FOUND；扩展请求失败 -> UserError.OAUTH_AUTH_FAILED。
                    - 响应：成功时 302 重定向到 Provider 授权页。
                    """
    )
    @CheckLogin
    @GetMapping("/verify/initiateCampusOAuthIdentityVerify")
    public void initiateCampusOAuthIdentityVerify(@RequestParam("providerCode") @NotBlank String providerCode, HttpServletResponse response) throws IOException {
        String redirectUrl = (String) userVerificationService.initVerification(
                new UserVerificationInitStrategy.CampusProviderOAuthInitCommand(SecurityContextHolder.getUserId(), providerCode));
        response.sendRedirect(redirectUrl);
    }

    @Operation(
            summary = "接收校园身份认证 OAuth2.0 回调",
            description = """
                    - 用途：接收 OAuth2.0 身份认证回调，完成 state 对应用户的校园身份认证落库。
                    - 请求：code 为授权码；state 为发起认证时生成的一次性状态。
                    - 约束：state 必须有效且未过期，并从中解析出 Provider 与目标用户；该用户仍必须处于未完成身份认证状态。
                    - 处理：校验 state 后向扩展服务换取身份 claims，并按策略更新该用户身份资料、认证状态和奖励；不依赖当前登录上下文。
                    - 失败：state 无效或过期 -> UserError.OAUTH_AUTH_STATE_INVALID；Provider 未声明 OAuth2.0 身份认证 -> UserError.AUTH_CAMPUS_OAUTH_PROVIDER_NOT_FOUND；认证失败或资料缺失 -> UserError.OAUTH_AUTH_FAILED；账号状态不允许认证 -> UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID；学工号已被其他账号绑定 -> UserError.VERIFICATION_CAMPUS_NO_ALREADY_EXISTS。
                    - 响应：成功时返回空结果。
                    """
    )
    @GetMapping("/verify/campusOAuthIdentityVerifyCallback")
    public R<Void> campusOAuthIdentityVerifyCallback(@RequestParam("code") String code, @RequestParam("state") String state) {
        campusOAuth2FlowStrategy.completeIdentity(code, state);
        return R.ok();
    }

    @Deprecated
    @Operation(
            summary = "发起复旦 UIS 认证",
            description = """
                    - 用途：兼容旧前端入口，当前用户通过复旦 UIS 账号密码发起校园身份认证流程。
                    - 请求：uisAccount 为 UIS 账号；uisPassword 为 UIS 密码。
                    - 约束：当前用户必须已登录且仍处于未完成身份认证状态。
                    - 处理：将 UIS 凭据映射为 FUDAN Provider 输入，并复用校园身份认证通用流程；不在本接口直接返回最终认证资料。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；账号状态不允许认证 -> UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID；扩展请求失败 -> UserError.CAMPUS_CLASSIC_AUTH_REQUEST_FAILED。
                    - 响应：成功受理时返回空结果，认证进度通过状态检查接口查询。
                    """
    )
    @CheckLogin
    @PostMapping("/verify/initiateFudanUISVerify")
    @Log(title = "发起复旦UIS认证", businessType = BusinessType.OTHER, isSaveRequestData = false)
    public R<Void> initiateFudanUISVerify(
            @RequestParam("uisAccount") @NotBlank String uisAccount,
            @RequestParam("uisPassword") @NotBlank String uisPassword) {
        Map<String, String> map = new HashMap<>();
        map.put("account", uisAccount);
        map.put("password", uisPassword);
        userVerificationService.initVerification(
                new UserVerificationInitStrategy.CampusProviderClassicAuthInitCommand(
                        SecurityContextHolder.getUserId(), "FUDAN", map
                )
        );
        return R.ok();
    }

    @Deprecated
    @Operation(
            summary = "检查复旦 UIS 认证状态",
            description = """
                    - 用途：兼容旧前端入口，查询当前用户复旦 UIS 认证任务的处理结果。
                    - 请求：无需请求参数，目标用户来自当前认证上下文。
                    - 约束：当前用户必须已登录，并且已发起过 FUDAN 校园身份认证流程。
                    - 处理：复用校园身份认证通用流程查询任务状态，并在认证成功时根据标准身份资料更新用户身份和认证结果。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；账号状态不允许认证 -> UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID；任务失败或资料缺失 -> UserError.CAMPUS_CLASSIC_AUTH_FAILED；学工号已被其他账号绑定 -> UserError.VERIFICATION_CAMPUS_NO_ALREADY_EXISTS。
                    - 响应：返回认证结果信息。
                    """
    )
    @CheckLogin
    @GetMapping("/verify/checkFudanUISVerify")
    @Log(title = "检查复旦UIS认证状态", businessType = BusinessType.OTHER, isSaveResponseData = false)
    public R<VerificationResultDTO> checkFudanUISVerify() {
        ClassicAuthTaskResultDTO dto = campusClassicAuthFlowStrategy.checkCampusIdentity(
                SecurityContextHolder.getUserId(), "FUDAN", null
        );
        return R.ok(toLegacyFudanUISVerificationResult(dto));
    }

    /** 将标准校园身份任务结果转换为旧复旦前端仍使用的 VerificationResultDTO。 */
    private VerificationResultDTO toLegacyFudanUISVerificationResult(ClassicAuthTaskResultDTO taskResult) {
        if (taskResult.getState() == ClassicAuthTaskState.QUEUED
                || taskResult.getState() == ClassicAuthTaskState.PENDING) {
            return VerificationResultDTO.pending();
        }
        if (taskResult.getState() == ClassicAuthTaskState.CHALLENGE_REQUIRED) {
            String actionPayload = null;
            if (taskResult.getChallenge() != null && taskResult.getChallenge().getType() == LoginChallengeType.QR_SCAN) {
                if (taskResult.getChallenge().getArtifacts() != null) {
                    actionPayload = taskResult.getChallenge().getArtifacts().stream()
                            .filter(artifact -> "qrCode".equals(artifact.getName()))
                            .filter(artifact -> artifact.getEncoding() == ChallengeArtifactEncoding.BASE64)
                            .map(artifact -> artifact.getContent())
                            .findFirst()
                            .orElse(null);
                }
            }
            return VerificationResultDTO.builder()
                    .completed(false)
                    .requireAction(true)
                    .actionPayload(actionPayload)
                    .message(taskResult.getMessage())
                    .build();
        }
        if (taskResult.getState() == ClassicAuthTaskState.SUCCESS) {
            return VerificationResultDTO.success();
        }
        throw new ServiceException(UserError.CAMPUS_CLASSIC_AUTH_FAILED, taskResult.getMessage());
    }
}
