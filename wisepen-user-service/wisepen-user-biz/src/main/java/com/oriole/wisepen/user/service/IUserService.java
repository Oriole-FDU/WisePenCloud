package com.oriole.wisepen.user.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.*;
import com.oriole.wisepen.user.api.domain.dto.res.UserDetailInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.res.UserSearchUserResponse;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.domain.entity.UserProfileEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IUserService {

    // 根据账号获取用户信息
    UserEntity getUserCoreInfoByAccount(String account);
    // 根据 userId 获取用户详细信息
    UserDetailInfoResponse getUserInfoById(Long userId);
    // 根据 userId 列表获取用户展示信息
    Map<Long, UserDisplayBase> getUserDisplayInfoByIds(Set<Long> userIds);
    // 根据 userId 列表获取用户展示信息(额外字段)
    Map<Long, UserDisplayBase> getUserDisplayInfoByIds(Set<Long> userIds, boolean includePrivateFields);
    // 按完整用户名或邮箱搜索用户
    List<UserSearchUserResponse> searchUser(String keyword);
    // 查询当前用户小组范围内的用户搜索补全
    List<UserSearchUserResponse> listUserSearchSuggestions(Set<Long> groupIds, String keyword, Integer size);

    // 注册
    void register(AuthRegisterRequest req);

    // 发送重置密码邮件
    void sendResetMail(AuthPwdResetVerifyRequest req);
    // 重置密码
    void resetPassword(AuthPwdResetRequest req);

    // 更新用户信息
    void updateUserInfo(Long userId, UserInfoUpdateRequest req);
    // 更新用户Profile信息
    void updateProfile(Long userId, UserProfileUpdateRequest req);

    // 仅限管理员使用
    void resetPasswordAdmin(AuthPwdAdminResetRequest req);
    void updateUserInfoAdmin(UserInfoAdminUpdateRequest req);
    void updateProfileAdmin(UserProfileAdminUpdateRequest req);
    PageR<UserEntity> getUserListAdmin(int page, int size, String keyword, UserStatus userStatus, IdentityType identityType);
    UserProfileEntity getUserDetailInfoAdmin(Long userId);
}
