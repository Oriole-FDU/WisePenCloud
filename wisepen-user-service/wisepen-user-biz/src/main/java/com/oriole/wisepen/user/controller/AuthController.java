package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.user.api.domain.dto.LoginRequest;
import com.oriole.wisepen.user.api.domain.dto.RegisterRequest;
import com.oriole.wisepen.user.api.domain.dto.ResetRequest;
import com.oriole.wisepen.user.api.domain.dto.ResetExecuteRequest;
import com.oriole.wisepen.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<Void> login(@Valid @RequestBody LoginRequest loginRequest) {
        authService.login(loginRequest);
        return R.ok();
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    @PostMapping("/register")
    public R<String> register(@Valid @RequestBody RegisterRequest registerRequest) {
        String userId = authService.register(registerRequest);
        return R.ok(userId);
    }

    @PostMapping("/forgot-password/email")
    public R<Void> forgotPassword(@Valid @RequestBody ResetRequest resetRequest) {
        authService.sendResetMail(resetRequest);
        return R.ok();
    }

    @PostMapping("/forgot-password/reset")
    public R<Void> resetPassword(@Valid @RequestBody ResetExecuteRequest resetExecuteRequest) {
        authService.resetPassword(resetExecuteRequest);
        return R.ok();
    }
}