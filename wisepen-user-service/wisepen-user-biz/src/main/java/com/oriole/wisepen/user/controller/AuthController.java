package com.oriole.wisepen.user.controller;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.config.UserProperties;
import com.oriole.wisepen.user.api.domain.dto.req.AuthLoginRequest;
import com.oriole.wisepen.user.api.domain.dto.req.AuthRegisterRequest;
import com.oriole.wisepen.user.api.domain.dto.req.AuthPwdResetRequest;
import com.oriole.wisepen.user.api.domain.dto.req.AuthPwdResetVerifyRequest;
import com.oriole.wisepen.user.service.AuthService;
import com.oriole.wisepen.user.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.oriole.wisepen.common.core.constant.SecurityConstants.AUTHORIZATION_TOKEN;

@Tag(name = "用户认证", description = "用户登录、登出、注册与密码重置")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final IUserService userService;
    private final UserProperties userProperties;

    @Operation(
            summary = "用户登录",
            description = """
                    - 用途：用户使用账号密码建立后端登录会话。
                    - 请求：account 支持用户名或学工号；password 为登录密码；code 和 uuid 为验证码预留字段，当前接口不处理验证码校验。
                    - 约束：账号必须存在，账号状态不能为 BANNED，密码必须与已保存密文匹配。
                    - 处理：校验通过后读取用户身份和小组角色，写入 Redis 会话，并在响应中设置认证 Cookie。
                    - 失败：账号不存在或密码错误 -> UserError.AUTH_USERNAME_OR_PASSWORD_INVALID；账号被锁定 -> UserError.AUTH_USER_LOCKED。
                    - 响应：返回 sessionId，可同时通过 Cookie 或请求头用于后续认证。
                    """
    )
    @PostMapping("/login")
    public R<String> login(@Valid @RequestBody AuthLoginRequest loginRequest, HttpServletResponse response) {
        String sessionId = authService.login(loginRequest);

        Cookie cookie = buildAuthCookie(sessionId, 7 * 24 * 60 * 60);
        response.addCookie(cookie);
        // 返回 sessionId，以备加入请求头中
        return R.ok(sessionId);
    }

    @Operation(
            summary = "用户登出",
            description = """
                    - 用途：当前登录用户主动结束后端会话。
                    - 请求：无需请求体，当前会话标识来自认证上下文。
                    - 约束：当前用户必须已登录；会话标识为空时不会执行 Redis 会话删除。
                    - 处理：删除当前用户的 Redis 会话，并向浏览器写入过期认证 Cookie；不影响同一用户的其他会话。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：成功时返回空结果。
                    """
    )
    @CheckLogin
    @PostMapping("/logout")
    public R<Void> logout(HttpServletResponse response) {
        String sessionId = SecurityContextHolder.getUserAuthToken();

        if (StrUtil.isNotBlank(sessionId)) {
            authService.logout(sessionId, SecurityContextHolder.getUserId());
        }

        // 创建一个同名、同路径的空 Cookie
        Cookie clearCookie = buildAuthCookie(null, 0); // Max-Age=0 会强制浏览器立刻彻底删除该 Cookie
        response.addCookie(clearCookie);
        return R.ok();
    }

    private Cookie buildAuthCookie (String value, Integer maxAge) {
        Cookie cookie = new Cookie(AUTHORIZATION_TOKEN, value);
        cookie.setPath("/");
        if (StrUtil.isNotBlank(userProperties.getAuthCookieDomain())) {
            cookie.setDomain(userProperties.getAuthCookieDomain());
        }
        cookie.setHttpOnly(true); // 严禁前端 JS 读取，防 XSS
        // cookie.setSecure(true); // HTTPS 务必开启此项
        cookie.setMaxAge(maxAge); // 7天
        return cookie;
    }

    @Operation(
            summary = "用户注册",
            description = """
                    - 用途：创建一个待身份认证的新学生账号。
                    - 请求：username 为新账号用户名；password 为初始密码，需满足密码格式校验。
                    - 约束：username 必须全局唯一，且符合用户名校验规则。
                    - 处理：创建 UNIDENTIFIED 状态的学生用户，写入密码密文，并初始化用户资料和钱包记录；不自动登录，不自动完成身份认证。
                    - 失败：用户名重复 -> UserError.USERNAME_ALREADY_EXISTS。
                    - 响应：成功时返回空字符串结果。
                    """
    )
    @PostMapping("/register")
    public R<String> register(@Valid @RequestBody AuthRegisterRequest req) {
        userService.register(req);
        return R.ok();
    }

    @Operation(
            summary = "发送密码重置邮件",
            description = """
                    - 用途：为已完成身份认证的用户发起密码重置流程。
                    - 请求：username 指定申请重置密码的用户账号。
                    - 约束：用户必须存在且不能处于 UNIDENTIFIED 状态；不存在的用户名会静默终止以避免账号探测。
                    - 处理：生成密码重置 token，渲染重置邮件模板，并通过系统邮件服务发送邮件；不立即修改密码。
                    - 失败：用户未完成身份认证 -> UserError.CANNOT_OPERATE_BEFORE_AUTH_VERIFICATION；重置密码邮件发送失败 -> UserError.USER_PASSWORD_RESET_EMAIL_SEND_FAILED。
                    - 响应：成功受理或用户名不存在静默终止时返回空结果。
                    """
    )
    @PostMapping("/sendResetMail")
    public R<Void> sendResetMail(@Valid @RequestBody AuthPwdResetVerifyRequest req) {
        userService.sendResetMail(req);
        return R.ok();
    }

    @Operation(
            summary = "重置用户密码",
            description = """
                    - 用途：用户通过密码重置 token 设置新密码。
                    - 请求：token 为密码重置邮件中的一次性凭证；newPassword 为新密码。
                    - 约束：token 必须有效且未过期，新密码必须满足密码格式校验。
                    - 处理：按 token 定位用户并更新密码密文，同时删除该用户已存在的登录会话，强制重新登录。
                    - 失败：重置 token 失效或过期 -> UserError.USER_PASSWORD_RESET_EXPIRED。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/resetPassword")
    public R<Void> resetPassword(@Valid @RequestBody AuthPwdResetRequest req) {
        userService.resetPassword(req);
        return R.ok();
    }

}
