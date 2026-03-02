package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.api.domain.dto.RegisterRequest;
import com.oriole.wisepen.user.api.domain.dto.ResetExecuteRequest;
import com.oriole.wisepen.user.api.domain.dto.ResetRequest;
import com.oriole.wisepen.user.api.domain.dto.UserInfoDTO;
import com.oriole.wisepen.user.domain.entity.User;

public interface UserService {
    User getUserCoreInfoByAccount(String account);
    UserInfoDTO getUserInfoById(Long userId);

    void register(RegisterRequest registerRequest);
    void sendResetMail(ResetRequest resetRequest);
    void resetPassword(ResetExecuteRequest resetExecuteRequest);

    // 更新用户资料（sys_user + sys_user_profile）
    void updateProfile(Long userId, UserInfoDTO profileDto);

    // 发起邮箱验证（生成 token 并发送邮件）
    void initiateEmailVerify(Long userId, int suffixType);

    // 验证 token 回调，返回是否验证成功
    boolean checkVerifyToken(String token);
}