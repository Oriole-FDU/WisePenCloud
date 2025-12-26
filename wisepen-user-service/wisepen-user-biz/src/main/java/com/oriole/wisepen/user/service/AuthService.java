package com.oriole.wisepen.user.service;

import cn.dev33.satoken.SaManager;
import com.oriole.wisepen.common.core.domain.R;
import cn.dev33.satoken.stp.SaLoginConfig;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.oriole.wisepen.common.core.domain.enums.ResultCode;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.dto.LoginBody;
import com.oriole.wisepen.user.api.domain.dto.RegisterBody;
import com.oriole.wisepen.user.api.domain.dto.ResetBody;
import com.oriole.wisepen.user.domain.entity.User;
import com.oriole.wisepen.user.domain.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final GroupService groupService;

    /**
     * 登录逻辑
     */
    public void login(LoginBody loginBody) {
        String account = loginBody.getAccount();
        String password = loginBody.getPassword();

        // 查询用户信息 (包含密码密文)
        User user = userService.getUserCoreInfoByAccount(account);
        // 账号不存在
        if (user == null) {
            throw new ServiceException(ResultCode.USER_PASSWORD_ERROR);
        }
        String username = user.getUsername();

        // 校验账号状态 除0，禁用状态外都允许登录
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ServiceException(ResultCode.USER_LOCKED);
        }

        // 校验密码 (BCrypt)
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new ServiceException(ResultCode.USER_PASSWORD_ERROR);
        }

        // 获取该用户所属的所有 Group ID
        List<Long> groupIds = groupService.getGroupIdsByUserId(user.getId());
        String groupIdsStr = StrUtil.join(",", groupIds); // 转成字符串

        // 获取 Sa-Token 配置的有效期 (单位是秒)
        long timeout = SaManager.getConfig().getTimeout();
        // 计算过期时间戳 (当前时间秒数 + 有效期秒数)
        long expTime = System.currentTimeMillis() / 1000 + timeout;

        // Sa-Token 登录
        // 这里我们将 IdentityType 和 GroupIds 一起注入 Token Session，这样网关层 (APISIX) 就能读到这些数据并透传了
        StpUtil.login(user.getId(),
                SaLoginConfig.setExtra("identityType", user.getIdentityType())
                        .setExtra("groupIds", groupIdsStr)
                        .setExtra("key", "wisepen-app")
                        .setExtra("exp", expTime)
        );

        log.info("用户登录成功: username={}, id={}, groups={}", username, user.getId(), groupIdsStr);

    }

    /**
     * 注销
     */
    public void logout() {
        StpUtil.logout();
    }

    /**
     * 注册
     */
    @Transactional(rollbackFor = Exception.class)//同生死
    public R<String> register(RegisterBody registerBody) {
            String username = registerBody.getUsername();
            //查询用户名是否存在
            if(userService.verifyExistUsername(username)){
                throw new ServiceException(ResultCode.USERNAME_EXISTED);
            }

            String campusNum =  registerBody.getCampusNum();
            if(userService.verifyExistCampusNum(campusNum)){
                throw new ServiceException(ResultCode.USER_CAMPUS_NUM_EXISTED);
            }

            String password = registerBody.getPassword();
            String realName = registerBody.getRealName();
            // 创建用户对象
            User user = new User();
            user.setUsername(username);
            //默认nickname与username同名
            user.setNickname(username);
            //哈希
            user.setPassword(BCrypt.hashpw(password));
            user.setIdentityType(1);
            user.setStatus(1);
            //创建时间
            user.setCreateTime(LocalDateTime.MAX);
            user.setUpdateTime(LocalDateTime.MAX);
            // 插入用户基本信息
            boolean userSaved = userService.insertUser(user);
            if(!userSaved){
                throw new ServiceException(ResultCode.SYSTEM_ERROR);
            }
            Long userId = user.getId();

            // 插入用户档案信息
            UserProfile userProfile = new UserProfile();
            userProfile.setUserId(userId);
            userProfile.setRealName(realName.trim());
            userProfile.setCampusNo(campusNum.trim());
            userProfile.setCollege("复旦大学");

            boolean userProfileSaved = userService.insertUserProfile(userProfile);
            if(!userProfileSaved){
                throw new ServiceException(ResultCode.SYSTEM_ERROR);
            }
            return R.ok(userId.toString());
    }

    /**
     * 发送重置邮件
     */
    @Autowired
    StringRedisTemplate redisTemplate;
    public R<Void> sendResetMail(ResetBody resetBody){
        String campusNum = resetBody.getCampusNum();
        if(userService.verifyExistCampusNum(campusNum)){
            Long userId = userService.getUserIdByCampusNum(campusNum);
            String targetEmail = userService.getUserEmailByCampusNum(campusNum);
            String token = UUID.randomUUID().toString();
            String redisKey = "auth:reset:token:" + token;
            redisTemplate.opsForValue().set(redisKey, String.valueOf(userId),15, TimeUnit.MINUTES);


        }
        return R.ok();
    }

    /**
     * 执行重置密码
     */
    public R<Void> resetPassword(ResetBody resetBody){
        return R.ok();
    }
}