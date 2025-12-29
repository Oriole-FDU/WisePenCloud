package com.oriole.wisepen.user.service;

import cn.dev33.satoken.SaManager;
import com.oriole.wisepen.common.core.domain.R;
import cn.dev33.satoken.stp.SaLoginConfig;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.oriole.wisepen.common.core.domain.enums.ResultCode;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.exception.UserErrorCode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.oriole.wisepen.user.api.domain.dto.LoginRequest;
import com.oriole.wisepen.user.api.domain.dto.RegisterRequest;
import com.oriole.wisepen.user.api.domain.dto.ResetRequest;
import com.oriole.wisepen.user.api.domain.dto.ResetExecuteRequest;
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
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 内部Feign客户端，用于调用system-service的邮件发送接口
     */
    @FeignClient(value = "wisepen-system-service", contextId = "internalMailService")
    interface InternalMailService {
        @PostMapping("/system/mail/send-reset-password")
        Map<String, Object> sendResetPasswordMail(@RequestBody Map<String, String> mailRequest);
    }

    private final UserService userService;
    private final GroupService groupService;
    private final InternalMailService internalMailService;

    /**
     * 登录逻辑
     */
    public void login(LoginRequest loginRequest) {
        String account = loginRequest.getAccount();
        String password = loginRequest.getPassword();

        // 查询用户信息 (包含密码密文)
        User user = userService.getUserCoreInfoByAccount(account);
        // 账号不存在
        if (user == null) {
            throw new ServiceException(UserErrorCode.USER_PASSWORD_ERROR);
        }
        String username = user.getUsername();

        // 校验账号状态 除0，禁用状态外都允许登录
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ServiceException(UserErrorCode.USER_LOCKED);
        }

        // 校验密码 (BCrypt)
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new ServiceException(UserErrorCode.USER_PASSWORD_ERROR);
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
    public String register(RegisterRequest registerRequest) {
            String username = registerRequest.getUsername();
            //查询用户名是否存在
            if(userService.verifyExistUsername(username)){
                throw new ServiceException(UserErrorCode.USERNAME_EXISTED);
            }

            String campusNum =  registerRequest.getCampusNum();
            if(userService.verifyExistCampusNum(campusNum)){
                throw new ServiceException(UserErrorCode.USER_CAMPUS_NUM_EXISTED);
            }

            String password = registerRequest.getPassword();
            String realName = registerRequest.getRealName();
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
            //创建时间
            LocalDateTime now = LocalDateTime.now();
            user.setCreateTime(now);
            user.setUpdateTime(now);
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
            return userId.toString();
    }

    /**
     * 发送重置邮件
     */
    @Autowired
    StringRedisTemplate redisTemplate;

    public R<Void> sendResetMail(ResetRequest resetRequest){
        String campusNum = resetRequest.getCampusNum();
        String mailAppendix = resetRequest.getMailAppendix();
        if(userService.verifyExistCampusNum(campusNum)){
            Long userId = userService.getUserIdByCampusNum(campusNum);
            String targetEmail = userService.getUserEmailByCampusNum(campusNum);
            //没查到生成默认邮箱
            if(targetEmail == null ){
                targetEmail = campusNum + mailAppendix;
            }

            String token = UUID.randomUUID().toString();
            String redisKey = "auth:reset:token:" + token;
            redisTemplate.opsForValue().set(redisKey, String.valueOf(userId),15, TimeUnit.MINUTES);

            //重置链接
            String resetUrl = "https://wisepen.fudan.edu.cn/reset-pwd?token=" + token;

            try {
                // 构建邮件发送请求数据
                Map<String, String> mailRequest = new HashMap<>();
                mailRequest.put("toEmail", targetEmail);
                mailRequest.put("studentId", campusNum);
                mailRequest.put("resetLink", resetUrl);

                // 通过内部FeignClient调用system-service的邮件发送接口
                log.info("正在发送密码重置邮件：学号={}, 邮箱={}", campusNum, targetEmail);
                Map<String, Object> response = internalMailService.sendResetPasswordMail(mailRequest);

                // 处理响应
                if (response != null && response.get("data") != null) {
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    Boolean success = (Boolean) data.get("success");
                    if (success == null || !success) {
                        String errorMsg = data.get("errorMessage") != null ? (String) data.get("errorMessage") : "邮件发送失败";
                        throw new RuntimeException(errorMsg);
                    }
                } else {
                    throw new RuntimeException("邮件服务返回数据格式错误");
                }

                log.info("密码重置邮件发送成功：学号={}, 邮箱={}", campusNum, targetEmail);
            } catch (Exception e) {
                log.error("发送密码重置邮件失败：学号={}, 邮箱={}, 错误={}", campusNum, targetEmail, e.getMessage(), e);
                throw new ServiceException(UserErrorCode.EMAIL_SEND_ERROR);
            }
        }
        return R.ok();
    }

    /**
     * 直接根据用户ID更新密码（管理员用）
     * @param userId 用户ID
     * @param newPassword 新密码明文
     * @return 更新结果
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePasswordByUserId(String userId, String newPassword) {
        // 验证用户是否存在
        boolean userexist = userService.verifyExistUserId(userId);
        if (!userexist) {
            throw new ServiceException(UserErrorCode.USER_NOT_EXIST);
        }

        // 对新密码进行哈希处理
        String newPasswordHash = BCrypt.hashpw(newPassword);

        // 更新密码
        boolean updated = userService.updatePasswordByUserId(userId, newPasswordHash);

        if (!updated) {
            throw new ServiceException(UserErrorCode.PASSWORD_RESET_FAILED);
        }

        log.info("用户 {} 密码更新成功", userId);
    }

    /**
     * 执行重置密码（通过token）
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetExecuteRequest resetExecuteRequest){
        String redisKey = "auth:reset:token:" + resetExecuteRequest.getToken();
        log.info("正在尝试从Redis读取Key: {}", redisKey);
        String userId = redisTemplate.opsForValue().get(redisKey);
        if(userId == null){
            throw new ServiceException(UserErrorCode.USER_NOT_EXIST);
        }
        String newPasswordHash = BCrypt.hashpw(resetExecuteRequest.getNewPassword());

        boolean updated = userService.updatePasswordByUserId(userId, newPasswordHash);

        if (!updated) {
            throw new ServiceException(UserErrorCode.PASSWORD_RESET_FAILED);
        }

        // 成功后立即清理 Token
        redisTemplate.delete(redisKey);

        log.info("用户 {} 密码重置成功", userId);
    }
}