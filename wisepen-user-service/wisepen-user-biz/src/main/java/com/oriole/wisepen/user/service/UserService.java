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
}