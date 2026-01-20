package com.oriole.wisepen.user.service;

import cn.dev33.satoken.SaManager;
import cn.hutool.core.collection.CollUtil;
import cn.dev33.satoken.stp.SaLoginConfig;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.exception.UserErrorCode;
import com.oriole.wisepen.user.api.domain.dto.LoginRequest;
import com.oriole.wisepen.user.domain.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

import static com.oriole.wisepen.user.api.enums.Status.BANNED;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final GroupService groupService;

    public void login(LoginRequest loginRequest) {
        String account = loginRequest.getAccount();

        // 查询用户信息 (包含密码密文)
        User user = userService.getUserCoreInfoByAccount(account);

        // 账号不存在
        if (user==null){
            throw new ServiceException(UserErrorCode.USER_PASSWORD_ERROR);
        }

        // 校验账号状态
        if (user.getStatus()==BANNED) {
            throw new ServiceException(UserErrorCode.USER_LOCKED);
        }

        // 校验密码
        if (!BCrypt.checkpw(loginRequest.getPassword(), user.getPassword())) {
            throw new ServiceException(UserErrorCode.USER_PASSWORD_ERROR);
        }

        // 获取组信息并转换为逗号分隔字符串
        List<Long> groupIds = groupService.getGroupIdsByUserId(user.getId());
        String groupIdsStr = CollUtil.join(groupIds, ",");

        // 计算 APISIX/JWT 需要的过期时间戳
        long expTime = (System.currentTimeMillis() / 1000) + SaManager.getConfig().getTimeout();

        //Sa-Token 登录及 Extra 数据注入
        StpUtil.login(user.getId(), SaLoginConfig.setExtra("identityType", user.getIdentityType())
                .setExtra("groupIds", groupIdsStr)
                .setExtra("key", "wisepen-app")
                .setExtra("exp", expTime));

        log.info("用户登录成功: account={}, id={}, groups={}", account, user.getId(), groupIdsStr);
    }

    /**
     * 注销
     */
    public void logout() {
        StpUtil.logout();
    }
}