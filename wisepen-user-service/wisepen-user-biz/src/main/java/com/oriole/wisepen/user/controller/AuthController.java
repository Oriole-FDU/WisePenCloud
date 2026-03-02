package com.oriole.wisepen.user.controller;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.user.api.domain.dto.LoginRequest;
import com.oriole.wisepen.user.api.domain.dto.RegisterRequest;
import com.oriole.wisepen.user.api.domain.dto.ResetExecuteRequest;
import com.oriole.wisepen.user.api.domain.dto.ResetRequest;
import com.oriole.wisepen.user.service.AuthService;
import com.oriole.wisepen.user.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

import static com.oriole.wisepen.common.core.constant.SecurityConstants.COOKIE_AUTHORIZATION_TOKEN;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public R<Void> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        String sessionId = authService.login(loginRequest);

        Cookie cookie = buildAuthCookie(sessionId, 7 * 24 * 60 * 60);
        response.addCookie(cookie);
        return R.ok();
    }

    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = null;

        Cookie cookie = WebUtils.getCookie(request, COOKIE_AUTHORIZATION_TOKEN);
        sessionId = (cookie != null) ? cookie.getValue() : null;

        if (StrUtil.isNotBlank(sessionId)) {
            authService.logout(sessionId);
        }

        // 创建一个同名、同路径的空 Cookie
        Cookie clearCookie = buildAuthCookie(null, 0); // Max-Age=0 会强制浏览器立刻彻底删除该 Cookie
        response.addCookie(clearCookie);
        return R.ok();
    }

    private Cookie buildAuthCookie (String value, Integer maxAge) {
        Cookie cookie = new Cookie(COOKIE_AUTHORIZATION_TOKEN, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true); // 严禁前端 JS 读取，防 XSS
        // cookie.setSecure(true); // HTTPS 务必开启此项
        cookie.setMaxAge(maxAge); // 7天
        return cookie;
    }

    @PostMapping("/register")
    public R<String> register(@Valid @RequestBody RegisterRequest registerRequest) {
        userService.register(registerRequest);
        return R.ok();
    }

    @PostMapping("/forgot-password/email")
    public R<Void> forgotPassword(@Valid @RequestBody ResetRequest resetRequest) {
        userService.sendResetMail(resetRequest);
        return R.ok();
    }

    @PostMapping("/forgot-password/reset")
    public R<Void> resetPassword(@Valid @RequestBody ResetExecuteRequest resetExecuteRequest) {
        userService.resetPassword(resetExecuteRequest);
        return R.ok();
    }


}