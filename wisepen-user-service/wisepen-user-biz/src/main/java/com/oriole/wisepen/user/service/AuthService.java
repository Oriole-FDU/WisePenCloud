package com.oriole.wisepen.user.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.api.domain.dto.req.AuthLoginRequest;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final IUserService userService;
    private final IGroupMemberService groupMemberService;
    private final RedisCacheManager redisCacheManager;

    public String login(AuthLoginRequest loginRequest) {
        String account = loginRequest.getAccount();

        // 查询用户信息 (包含密码密文)
        UserEntity user = userService.getUserCoreInfoByAccount(account);

        // 账号不存在
        if (user==null){
            throw new ServiceException(UserError.AUTH_USERNAME_OR_PASSWORD_INVALID);
        }

        // 校验账号状态
        if (user.getUserStatus()== UserStatus.BANNED) {
            throw new ServiceException(UserError.AUTH_USER_LOCKED);
        }

        // 校验密码
        if (!BCrypt.checkpw(loginRequest.getPassword(), user.getPassword())) {
            throw new ServiceException(UserError.AUTH_USERNAME_OR_PASSWORD_INVALID);
        }

        Map<String, Integer> groupRoleMap = groupMemberService.getGroupRoleMapByUserId(user.getUserId());

        String sessionId = redisCacheManager.setSession(user.getUserId(), user.getIdentityType(), user.getUserStatus(), groupRoleMap);
        log.info("login succeeded. account={} userId={} groupCount={}",
                account, user.getUserId(), groupRoleMap.size());
        return sessionId;
    }

    /**
     * 注销
     */
    public void logout(String sessionId, Long userId) {
        if (StrUtil.isBlank(sessionId)) {
            return;
        }
        redisCacheManager.deleteSession(sessionId, userId);
        log.info("logout succeeded. userId={}", userId);
    }
}
