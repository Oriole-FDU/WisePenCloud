package com.oriole.wisepen.user.controller;

<<<<<<< HEAD
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.UserInfoDTO;
import com.oriole.wisepen.user.service.UserService;
=======
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.user.api.domain.dto.RegisterRequest;
import com.oriole.wisepen.user.api.domain.dto.ResetExecuteRequest;
import com.oriole.wisepen.user.api.domain.dto.ResetRequest;
import com.oriole.wisepen.user.api.domain.dto.UserInfoDTO;
import com.oriole.wisepen.user.service.UserService;
import jakarta.validation.Valid;
>>>>>>> 2e7809a (feat(): 新增了注册、找回密码、重置密码相关功能，位于user-service的userService)
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

<<<<<<< HEAD
=======
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

>>>>>>> 2e7809a (feat(): 新增了注册、找回密码、重置密码相关功能，位于user-service的userService)
    /**
     * 获取用户信息
     * 场景：用户登录后，前端需要获取自己的详细资料展示在右上角
     */
<<<<<<< HEAD
    @CheckLogin // 确保登录了才能查
    @GetMapping("/info")
    @Log(title = "用户信息获取", businessType= BusinessType.SELECT, isSaveResponseData=false)
    public R<UserInfoDTO> getInfo() {
        long userId = Long.parseLong(SecurityContextHolder.getUserId());
=======
    @SaCheckLogin // 确保登录了才能查
    @GetMapping("/info")
    @Log(title = "用户信息获取", businessType= BusinessType.SELECT, isSaveResponseData=false)
    public R<UserInfoDTO> getInfo() {
        long userId = StpUtil.getLoginIdAsLong();
>>>>>>> 2e7809a (feat(): 新增了注册、找回密码、重置密码相关功能，位于user-service的userService)
        UserInfoDTO userInfo = userService.getUserInfoById(userId);
        if (userInfo != null) {
            // 返回给前端前把密码抹除
            userInfo.setPassword(null);
        }
        return R.ok(userInfo);
    }
}