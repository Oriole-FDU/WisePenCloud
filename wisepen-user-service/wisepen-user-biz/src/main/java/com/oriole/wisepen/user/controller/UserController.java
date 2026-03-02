package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.UserInfoDTO;
import com.oriole.wisepen.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取用户信息
     * 场景：用户登录后，前端需要获取自己的详细资料展示在右上角
     */
    @CheckLogin // 确保登录了才能查
    @GetMapping("/info")
    @Log(title = "用户信息获取", businessType= BusinessType.SELECT, isSaveResponseData=false)
    public R<UserInfoDTO> getInfo() {
        long userId = Long.parseLong(SecurityContextHolder.getUserId());
        UserInfoDTO userInfo = userService.getUserInfoById(userId);
        if (userInfo != null) {
            // 返回给前端前把密码抹除
            userInfo.setPassword(null);
        }
        return R.ok(userInfo);
    }

    /**
     * 更新用户资料
     */
    @CheckLogin
    @PutMapping("/profile")
    @Log(title = "更新用户资料", businessType = BusinessType.UPDATE)
    public R<Void> updateProfile(@RequestBody UserInfoDTO profileDto) {
        long userId = Long.parseLong(SecurityContextHolder.getUserId());
        userService.updateProfile(userId, profileDto);
        return R.ok();
    }

    /**
     * 发起邮箱验证
     */
    @CheckLogin
    @PostMapping("/verify/email")
    @Log(title = "发起邮箱验证", businessType = BusinessType.OTHER)
    public R<Void> initiateEmailVerify(@RequestParam("suffixType") int suffixType) {
        long userId = Long.parseLong(SecurityContextHolder.getUserId());
        userService.initiateEmailVerify(userId, suffixType);
        return R.ok();
    }

    /**
     * 学号验证回调
     */
    @GetMapping("/verify/check")
    @Log(title = "学号验证回调", businessType = BusinessType.OTHER)
    public R<Boolean> checkVerify(@RequestParam("token") String token) {
        boolean ok = userService.checkVerifyToken(token);
        return R.ok(ok);
    }
}