package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.api.domain.dto.UserInfoDTO;
import com.oriole.wisepen.user.api.enums.Status;
import com.oriole.wisepen.user.domain.entity.User;
import com.oriole.wisepen.user.domain.entity.UserProfile;

public interface UserService {
    User getUserCoreInfoByUsername(String username);
    User getUserCoreInfoByAccount(String account);
    UserInfoDTO getUserInfoById(Long userId);
    void updateUserStatus(Long userId, Status status);

    boolean verifyExistUsername(String username);
    boolean verifyExistUserId(String account);
    boolean verifyExistCampusNo(String campusNo);
    boolean insertUser(User user);
    boolean insertUserProfile(UserProfile userProfile);

    Long getUserIdByCampusNo(String campusNo);
    String getUserEmailByCampusNo(String campusNo);

    /**
     * 根据用户ID更新密码
     */
    boolean updatePasswordByUserId(String userId, String newPasswordHash);

}