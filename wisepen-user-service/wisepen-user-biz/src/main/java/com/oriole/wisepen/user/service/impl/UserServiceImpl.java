package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import java.util.UUID;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import com.oriole.wisepen.system.api.feign.RemoteMailService;
import com.oriole.wisepen.user.api.domain.dto.RegisterRequest;
import com.oriole.wisepen.user.api.domain.dto.ResetExecuteRequest;
import com.oriole.wisepen.user.api.domain.dto.ResetRequest;
import com.oriole.wisepen.user.api.domain.dto.UserInfoDTO;
import com.oriole.wisepen.user.api.enums.Status;
import com.oriole.wisepen.user.domain.entity.User;
import com.oriole.wisepen.user.domain.entity.UserProfile;
import com.oriole.wisepen.user.exception.UserErrorCode;
import com.oriole.wisepen.user.service.UserService;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;

    @Autowired
    StringRedisTemplate redisTemplate;

    private final TemplateEngine templateEngine;

    private final RemoteMailService remoteMailService;

    @Override
    public User getUserCoreInfoByAccount(String account) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .and(w -> w.eq(User::getUsername, account).or().eq(User::getCampusNo, account))
                .last("LIMIT 1"));
    }

    /**
     * 注册
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest registerRequest) {
        // 校验用户名是否存在
        if (userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, registerRequest.getUsername())) > 0) {
            throw new ServiceException(UserErrorCode.USERNAME_EXISTED);
        }

        // 新建未验证的学生用户
        User user = User.builder()
                .username(registerRequest.getUsername())
                .identityType(IdentityType.STUDENT)
                .status(Status.UNIDENTIFIED)
                .build();

        // 加密用户密码
        user.setPassword(BCrypt.hashpw(registerRequest.getPassword()));
        userMapper.insert(user);

        // 新建档案
        UserProfile userProfile = UserProfile.builder()
                .userId(user.getId())
                .university("复旦大学")
                .college("复旦大学")
                .build();
        userProfileMapper.insert(userProfile);
    }

    /**
     * 发送重置邮件
     */
    @Override
    public void sendResetMail(ResetRequest resetRequest) {
        // 查询学号对应用户
        String campusNo = resetRequest.getCampusNo();
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getCampusNo, campusNo).last("LIMIT 1"));

        if(user==null){
            log.warn("重置密码申请：学号 {} 不存在，流程静默终止", campusNo);
            return; // 处于安全考虑，不存在也不报错，防止撞库
        }

        String token = UUID.randomUUID().toString();

        String redisKey = "auth:reset:token:" + token;
        redisTemplate.opsForValue().set(redisKey, String.valueOf(user.getId()), 15, TimeUnit.MINUTES);

        // 构建重置链接
        String resetLink = "https://wisepen.fudan.edu.cn/reset-pwd?token=" + token;

        // 构建重置邮件
        Context context = new Context();
        context.setVariable("student_id", campusNo);
        context.setVariable("reset_link", resetLink);
        context.setVariable("current_date", DateUtil.now());
        // Thymeleaf 渲染
        String emailContent = templateEngine.process("resetMailTemplate", context);

        // 构造邮件 DTO 并发送
        MailSendDTO mailDTO = MailSendDTO.builder()
                .toEmail(user.getEmail())
                .subject("密码重置申请")
                .content(emailContent) // 传递渲染后的 HTML 字符串
                .build();

        try {
            remoteMailService.sendMail(mailDTO);
            log.info("Email sent. campusNo={}, email={}", campusNo, user.getEmail());
        } catch (Exception e) {
            log.error("Email sending failed.", e);
            throw new ServiceException(UserErrorCode.EMAIL_SEND_ERROR);
        }
    }

    @Override
    public UserInfoDTO getUserInfoById(Long userId) {
        // 查核心账号
        User user = userMapper.selectById(userId);

        if (user == null) {
            return null;
        }

        // 查档案详情
        UserProfile profile = userProfileMapper.selectById(user.getId());

        // 组装 DTO
        UserInfoDTO dto = new UserInfoDTO();
        BeanUtil.copyProperties(user, dto);

        if (profile != null) {
            BeanUtil.copyProperties(profile, dto);
        }

        return dto;
    }

    /**
     * 更新用户资料（更新 sys_user 与 sys_user_profile）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, UserInfoDTO profileDto) {
        // 加载现有实体
        User existingUser = userMapper.selectById(userId);
        if (existingUser == null) {
            throw new ServiceException(UserErrorCode.USER_NOT_EXIST);
        }
        UserProfile existingProfile = userProfileMapper.selectById(userId);
        if (existingProfile == null) {
            // 若档案不存在则新建一个基础档案
            existingProfile = UserProfile.builder().userId(userId).build();
        }

        IdentityType identity = existingUser.getIdentityType();

        // 复制非空字段到现有实体
        BeanUtil.copyProperties(profileDto, existingUser, CopyOptions.create().setIgnoreNullValue(true));
        BeanUtil.copyProperties(profileDto, existingProfile, CopyOptions.create().setIgnoreNullValue(true));

        // 按身份过滤字段
        if (identity == IdentityType.STUDENT) {
            existingProfile.setAcademicTitle(existingProfile.getAcademicTitle()); // 保持原值（无操作）
        } else if (identity == IdentityType.TEACHER) {
            existingProfile.setMajor(existingProfile.getMajor());
            existingProfile.setClassName(existingProfile.getClassName());
        }

        // 更新两张表
        int r1 = userMapper.updateById(existingUser);
        int r2;
        if (userProfileMapper.selectById(userId) == null) {
            r2 = userProfileMapper.insert(existingProfile);
        } else {
            r2 = userProfileMapper.updateById(existingProfile);
        }

        if (r1 == 0 || r2 == 0) {
            throw new ServiceException(UserErrorCode.UPDATE_FAILED);
        }
    }

    /**
     * 执行重置密码（通过token）
     */
    @Override
    public void resetPassword(ResetExecuteRequest resetExecuteRequest){
        String redisKey = "auth:reset:token:" + resetExecuteRequest.getToken();
        String userId = redisTemplate.opsForValue().get(redisKey);

        if (userId == null) {
            throw new ServiceException(UserErrorCode.PASSWORD_RESET_FAILED);
        }

        updatePasswordByUserId(userId, resetExecuteRequest.getNewPassword());
        // 成功后立即清理 Token
        redisTemplate.delete(redisKey);
        log.info("用户 {} 密码重置成功", userId);
    }

    /**
     * 发起邮箱验证（根据学号和后缀类型拼接邮箱，生成6位数字token并存 redis，发送邮件）
     */
    @Override
    public void initiateEmailVerify(Long userId, int suffixType) {
        // 获取邮箱
        User user = userMapper.selectById(userId);
        String email = getEmail(suffixType, user);

        // 生成6位数字 token
        String token = RandomUtil.randomNumbers(6);

        String redisKey = "verify:token:" + token;
        String redisValue = userId + ":" + email;
        redisTemplate.opsForValue().set(redisKey, redisValue, 15, TimeUnit.MINUTES);

        // 构建邮件内容
        String content = "您的验证码为: " + token + "\n(该验证码15分钟内有效)";

        MailSendDTO mailDTO = MailSendDTO.builder()
                .toEmail(email)
                .subject("邮箱验证验证码")
                .content(content)
                .build();

        try {
            remoteMailService.sendMail(mailDTO);
            log.info("Verify email sent. userId={}, email={}", userId, email);
        } catch (Exception e) {
            log.error("Verify email sending failed.", e);
            throw new ServiceException(UserErrorCode.EMAIL_SEND_ERROR);
        }
    }

    private static String getEmail(int suffixType, User user) {
        if (user == null) {
            throw new ServiceException(UserErrorCode.USER_NOT_EXIST);
        }

        if (user.getStatus() != Status.UNIDENTIFIED) {
            throw new ServiceException(UserErrorCode.USER_STATUS_ERROR);
        }

        String campusNo = user.getCampusNo();
        if (campusNo == null) {
            throw new ServiceException(UserErrorCode.USER_NOT_EXIST);
        }

        // 简单后缀映射：0 -> @m.fudan.edu.cn, 1 -> @fudan.edu.cn
        String suffix = suffixType == 1 ? "@fudan.edu.cn" : "@m.fudan.edu.cn";
        return campusNo + suffix;
    }

    /**
     * 验证 token 并更新用户状态和邮箱
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean checkVerifyToken(String token) {
        String redisKey = "verify:token:" + token;
        String val = redisTemplate.opsForValue().get(redisKey);
        if (val == null) {
            return false;
        }

        // 删除 key 防止复用
        redisTemplate.delete(redisKey);

        String[] parts = val.split(":", 2);
        if (parts.length < 2) {
            return false;
        }

        Long userId = Long.valueOf(parts[0]);
        String email = parts[1];

        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setEmail(email);
        updateUser.setStatus(Status.NORMAL);
        updateUser.setUpdateTime(java.time.LocalDateTime.now());

        int r = userMapper.updateById(updateUser);
        return r > 0;
    }

    boolean updatePasswordByUserId(String userId, String newPassword) {
        User user = User.builder()
                .id(Long.valueOf(userId))
                .password(BCrypt.hashpw(newPassword))
                .updateTime(java.time.LocalDateTime.now())
                .build();
        int result = userMapper.updateById(user);
        return result > 0;
    }
}