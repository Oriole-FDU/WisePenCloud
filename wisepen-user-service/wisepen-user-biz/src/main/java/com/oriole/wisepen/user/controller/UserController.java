package com.oriole.wisepen.user.controller;

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
    @SaCheckLogin // 确保登录了才能查
    @GetMapping("/info")
    @Log(title = "用户信息获取", businessType= BusinessType.SELECT, isSaveResponseData=false)
    public R<UserInfoDTO> getInfo() {
        long userId = StpUtil.getLoginIdAsLong();
        UserInfoDTO userInfo = userService.getUserInfoById(userId);
        if (userInfo != null) {
            // 返回给前端前把密码抹除
            userInfo.setPassword(null);
        }
        return R.ok(userInfo);
    }
}