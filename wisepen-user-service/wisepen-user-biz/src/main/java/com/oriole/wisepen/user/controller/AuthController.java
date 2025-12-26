package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.user.api.domain.dto.LoginBody;
import com.oriole.wisepen.user.api.domain.dto.RegisterBody;
import com.oriole.wisepen.user.api.domain.dto.ResetBody;
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
    public R<Void> login(@Valid @RequestBody LoginBody loginBody) {
        authService.login(loginBody);
        return R.ok();
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    @PostMapping("/register")
    public R<String> register(@Valid @RequestBody RegisterBody registerBody){
        return authService.register(registerBody);
    }
    @PostMapping("/forgot-password/email")
    public R<Void> forgotPassword(@RequestBody ResetBody resetBody){
        return authService.sendResetMail(resetBody);
    }
    @PostMapping("/forgot-password/reset")
    public R<Void> resetPassword(@RequestBody ResetBody resetBody){
        return authService.resetPassword(resetBody);
    }
}
