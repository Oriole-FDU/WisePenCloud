package com.oriole.wisepen.user.service;

import cn.dev33.satoken.SaManager;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.oriole.wisepen.common.core.domain.R;
import cn.dev33.satoken.stp.SaLoginConfig;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.oriole.wisepen.common.core.domain.enums.ResultCode;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.domain.dto.MailResultDTO;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import com.oriole.wisepen.system.api.feign.RemoteMailService;
import com.oriole.wisepen.user.api.enums.IdentityType;
import com.oriole.wisepen.user.api.enums.Status;
import com.oriole.wisepen.user.exception.UserErrorCode;
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
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final GroupService groupService;
    private final RemoteMailService remoteMailService;

    public void login(LoginRequest loginRequest) {
        String account = loginRequest.getAccount();

        //获取并校验用户是否存在
        User user = Optional.ofNullable(userService.getUserCoreInfoByAccount(account))
                .orElseThrow(() -> new ServiceException(UserErrorCode.USER_PASSWORD_ERROR));

        //校验账号状态 (0表示锁定/禁用)
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new ServiceException(UserErrorCode.USER_LOCKED);
        }

        //校验密码
        if (!BCrypt.checkpw(loginRequest.getPassword(), user.getPassword())) {
            throw new ServiceException(UserErrorCode.USER_PASSWORD_ERROR);
        }

        //获取组信息并转换为逗号分隔字符串
        List<Long> groupIds = groupService.getGroupIdsByUserId(user.getId());
        String groupIdsStr = CollUtil.join(groupIds, ",");

        //计算 APISIX/JWT 需要的过期时间戳
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

    /**
     * 注册
     */

    @Transactional(rollbackFor = Exception.class)
    public String register(RegisterRequest registerRequest) {
        //业务校验
        validateRegisterRequest(registerRequest);

        //构建并插入基础信息
        User user = User.builder()
                .username(registerRequest.getUsername())
                .campusNo(registerRequest.getCampusNo())
                .nickname(registerRequest.getUsername())
                .password(BCrypt.hashpw(registerRequest.getPassword()))
                .identityType(IdentityType.STUDENT)
                .status(Status.NORMAL)
                .build();

        if (!userService.insertUser(user)) {
            throw new ServiceException(ResultCode.SYSTEM_ERROR);
        }

        //构建并插入档案信息（user.getId() 已被 MyBatis-Plus 自动回填）
        UserProfile profile = UserProfile.builder()
                .userId(user.getId())
                .realName(registerRequest.getRealName().trim())
                .campusNo(registerRequest.getCampusNo().trim())
                .college("复旦大学")
                .build();

        if (!userService.insertUserProfile(profile)) {
            throw new ServiceException(ResultCode.SYSTEM_ERROR);
        }

        return user.getId().toString();
    }

    /**
     * 校验逻辑
     */
    private void validateRegisterRequest(RegisterRequest request) {
        if (userService.verifyExistUsername(request.getUsername())) {
            throw new ServiceException(UserErrorCode.USERNAME_EXISTED);
        }
        if (userService.verifyExistCampusNo(request.getCampusNo())) {
            throw new ServiceException(UserErrorCode.USER_CAMPUS_NUM_EXISTED);
        }
    }

    /**
     * 发送重置邮件
     */
    @Autowired
    StringRedisTemplate redisTemplate;

    public R<Void> sendResetMail(ResetRequest resetRequest) {
        String campusNum = resetRequest.getCampusNo();

        //卫语句：校验学号是否存在
        if (!userService.verifyExistCampusNo(campusNum)) {
            return R.ok();
        }

        // 准备数据
        Long userId = userService.getUserIdByCampusNo(campusNum);
        String email = Optional.ofNullable(userService.getUserEmailByCampusNo(campusNum))
                .orElse(campusNum + resetRequest.getMailAppendix());
        String token = IdUtil.fastSimpleUUID();

        //存入 Redis (15分钟)
        redisTemplate.opsForValue().set("auth:reset:token:" + token, String.valueOf(userId), 15, TimeUnit.MINUTES);

        //构建邮件并发送
        MailSendDTO mailDTO = MailSendDTO.builder()
                .toEmail(email)
                .subject("【WisePen】密码重置申请")
                .template("resetMailTemplate")
                .templateParams(Map.of(
                        "student_id", campusNum,
                        "reset_link", "https://wisepen.fudan.edu.cn/reset-pwd?token=" + token,
                        "current_date", DateUtil.now()
                ))
                .build();

        try {
            log.info("发送重置邮件：学号={}, 邮箱={}", campusNum, email);
            R<MailResultDTO> res = remoteMailService.sendMail(mailDTO);

            if (res == null || res.getCode() != 200) {
                throw new ServiceException(UserErrorCode.EMAIL_SEND_ERROR);
            }
        } catch (Exception e) {
            log.error("邮件服务调用失败", e);
            throw new ServiceException(UserErrorCode.EMAIL_SEND_ERROR);
        }

        return R.ok();
    }

    /**
     * 直接根据用户ID更新密码（管理员用）
     * @param userId 用户ID
     * @param newPassword 新密码明文
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