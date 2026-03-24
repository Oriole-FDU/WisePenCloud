package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import com.oriole.wisepen.system.api.feign.RemoteMailService;
import com.oriole.wisepen.user.api.config.UserProperties;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserInfoBase;
import com.oriole.wisepen.user.api.domain.base.UserProfileBase;
import com.oriole.wisepen.user.api.domain.dto.req.*;
import com.oriole.wisepen.user.api.domain.dto.res.UserDetailInfoResponse;
import com.oriole.wisepen.user.api.enums.Status;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.domain.entity.UserProfileEntity;
import com.oriole.wisepen.user.domain.entity.UserTokenPoolEntity;
import com.oriole.wisepen.user.exception.UserErrorCode;
import com.oriole.wisepen.user.mapper.UserWalletsMapper;
import com.oriole.wisepen.user.service.UserService;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.mapper.UserProfileMapper;
import com.oriole.wisepen.user.strategy.UserVerificationStrategy;
import com.oriole.wisepen.user.strategy.VerificationStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserWalletsMapper userWalletsMapper;
    private final RedisCacheManager redisCacheManager;

    private final TemplateEngine templateEngine;
    private final RemoteMailService remoteMailService;

    private final UserProperties userProperties;

    @Autowired
    private VerificationStrategyFactory strategyFactory;

    @Override
    public UserEntity getUserCoreInfoByAccount(String account) {
        return userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .and(w -> w.eq(UserEntity::getUsername, account).or().eq(UserEntity::getCampusNo, account))
                .last("LIMIT 1"));
    }

    @Override
    public UserDisplayBase getUserDisplayInfoById(Long userId) {
        if (userId == null) {
            throw new ServiceException(UserErrorCode.USERNAME_EXISTED);
        }
        UserEntity userEntity = userMapper.selectById(userId);
        return BeanUtil.copyProperties(userEntity, UserDisplayBase.class);
    }

    @Override
    public Map<Long, UserDisplayBase> getUserDisplayInfoByIds(Set<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        List<UserEntity> userList = userMapper.selectBatchIds(userIds);

        if (CollectionUtils.isEmpty(userList)) {
            return Collections.emptyMap();
        }

        return userList.stream().filter(Objects::nonNull).collect(Collectors.toMap(
                UserEntity::getUserId,
                user -> BeanUtil.copyProperties(user, UserDisplayBase.class),
                (existing, replacement) -> existing
        ));
    }

    @Override
    public void register(AuthRegisterRequest req) {
        // 校验用户名是否存在
        if (userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, req.getUsername())) > 0) {
            throw new ServiceException(UserErrorCode.USERNAME_EXISTED);
        }

        // 新建未验证的学生用户
        UserEntity user = UserEntity.builder()
                .username(req.getUsername())
                .nickname(req.getUsername())
                .identityType(IdentityType.STUDENT)
                .status(Status.UNIDENTIFIED)
                .build();

        // 加密用户密码
        user.setPassword(BCrypt.hashpw(req.getPassword()));
        userMapper.insert(user);

        // 新建档案
        UserProfileEntity userProfile = UserProfileEntity.builder()
                .userId(user.getUserId())
                .build();
        userProfileMapper.insert(userProfile);

        UserTokenPoolEntity userWallets = new UserTokenPoolEntity();
        userWallets.setUserId(user.getUserId());
        userWallets.setTokenBalance(0);
        userWallets.setTokenUsed(0);
        userWalletsMapper.insert(userWallets);
    }

    @Override
    public void sendResetMail(AuthPwdResetVerifyRequest req) {
        // 查询学号对应用户
        String username = req.getUsername();
        UserEntity userEntity = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, username).last("LIMIT 1"));

        if(userEntity == null){
            log.warn("重置密码申请：用户名 {} 不存在，流程静默终止", username);
            return; // 处于安全考虑，不存在也不报错，防止撞库
        } else if(userEntity.getStatus() == Status.UNIDENTIFIED){
            // 未通过身份认证，不能找回密码
            throw new ServiceException(UserErrorCode.USER_UNIDENTIFIED);
        }
        // uid存入Redis
        String token = redisCacheManager.setPwdResetToken(userEntity.getUserId());
        // 构建重置链接
        String resetLink = userProperties.getApiDomain() + "/reset-pwd?token=" + token;

        // 构建重置邮件
        Context context = new Context();
        context.setVariable("username", username);
        context.setVariable("reset_link", resetLink);
        context.setVariable("current_date", DateUtil.now());
        // Thymeleaf 渲染
        String emailContent = templateEngine.process("resetMailTemplate", context);

        // 构造邮件 DTO 并发送
        MailSendDTO mailDTO = MailSendDTO.builder()
                .toEmail(userEntity.getEmail())
                .subject("WisePen 密码重置")
                .content(emailContent) // 传递渲染后的 HTML 字符串
                .build();

        try {
            remoteMailService.sendMail(mailDTO);
            log.info("Email sent. username={}, email={}", username, userEntity.getEmail());
        } catch (Exception e) {
            log.error("Email sending failed.", e);
            throw new ServiceException(UserErrorCode.EMAIL_SEND_ERROR);
        }
    }

    @Override
    public UserDetailInfoResponse getUserInfoById(Long userId) {
        UserEntity userEntity = userMapper.selectById(userId);
        UserProfileEntity userProfileEntity = userProfileMapper.selectById(userEntity.getUserId());

        // 组装响应
        UserDetailInfoResponse res = new UserDetailInfoResponse();
        res.setUserInfo(BeanUtil.copyProperties(userEntity, UserInfoBase.class));
        res.setUserProfile(BeanUtil.copyProperties(userProfileEntity, UserProfileBase.class));

        // 如果已验证
        if (userEntity.getStatus() != Status.UNIDENTIFIED) {
            // 设置只读字段
            List<String> readonlyFields = strategyFactory.getStrategy(userEntity.getVerificationMode()).getReadonlyFields();
            res.setReadonlyFields(readonlyFields);
        }
        return res;
    }

    @Override
    public void resetPasswordAdmin(AuthPwdAdminResetRequest req){
        Long userId = req.getUserId();
        updatePasswordByUserId(userId,
                StrUtil.isBlank(req.getNewPassword()) ? userProperties.getDefaultPassword() : req.getNewPassword());
        log.info("[管理员] 用户 {} 密码重置成功", userId);
    }

    @Override
    public void resetPassword(AuthPwdResetRequest req){
        Long userId = redisCacheManager.getPwdResetUser(req.getToken());
        if(userId == null){
            throw new ServiceException(UserErrorCode.PASSWORD_RESET_FAILED);
        }

        updatePasswordByUserId(userId, req.getNewPassword());
        log.info("用户 {} 密码重置成功", userId);
    }

    // 修改密码
    private void updatePasswordByUserId(Long userId, String newPassword) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password(BCrypt.hashpw(newPassword))
                .build();
        redisCacheManager.deleteSessionsByUserId(userId); // 强制下线
        userMapper.updateById(user);
    }

    @Override
    public void updateProfile(Long userId, UserProfileUpdateRequest req) {
        UserEntity userEntity = userMapper.selectById(userId);
        if(userEntity.getStatus() == Status.UNIDENTIFIED){
            // 未通过身份认证，不能更新Profile
            throw new ServiceException(UserErrorCode.USER_UNIDENTIFIED);
        }

        UserVerificationStrategy strategy = strategyFactory.getStrategy(userEntity.getVerificationMode());
        List<String> readonlyFields = strategy.getReadonlyFields();

        CopyOptions copyOptions = CopyOptions.create()
                .setIgnoreNullValue(true)
                .setIgnoreProperties(readonlyFields.toArray(new String[0]));

        UserProfileEntity userProfileEntity = new UserProfileEntity();
        BeanUtil.copyProperties(req, userProfileEntity, copyOptions);
        userProfileEntity.setUserId(userId);

        if (userEntity.getIdentityType().equals(IdentityType.STUDENT)) {
            userProfileEntity.setAcademicTitle(null);
        } else {
            userProfileEntity.setMajor(null);
            userProfileEntity.setClassName(null);
        }

        userProfileMapper.updateById(userProfileEntity);
    }

    @Override
    public void updateProfileAdmin(UserProfileAdminUpdateRequest req) {
        UserProfileEntity userProfileEntity = BeanUtil.copyProperties(req, UserProfileEntity.class);
        userProfileMapper.updateById(userProfileEntity);
    }

    public void updateUserInfo(Long userId, UserInfoUpdateRequest req) {
        UserEntity userEntity = BeanUtil.copyProperties(req, UserEntity.class);
        userEntity.setUserId(userId);
        userMapper.updateById(userEntity);
    }

    public void updateUserInfoAdmin(UserInfoAdminUpdateRequest req) {
        Long userId = req.getUserId();
        UserEntity userEntity = userMapper.selectById(userId);

        // 唯一性校验 username
        if (req.getUsername() != null && !req.getUsername().equals(userEntity.getUsername())) {
            if (userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                    .eq(UserEntity::getUsername, req.getUsername())
                    .ne(UserEntity::getUserId, userId)) > 0) {
                throw new ServiceException(UserErrorCode.USERNAME_EXISTED);
            }
        }
        // 唯一性校验 campusNo
        if (req.getCampusNo() != null && !req.getCampusNo().equals(userEntity.getCampusNo())) {
            if (userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                    .eq(UserEntity::getCampusNo, req.getCampusNo())
                    .eq(UserEntity::getStatus, Status.NORMAL)
                    .ne(UserEntity::getUserId, userId)) > 0) {
                throw new ServiceException(UserErrorCode.CAMPUS_NO_EXISTED);
            }
        }

        // 处理身份变更副作用
        if (req.getIdentityType() != null && !req.getIdentityType().equals(userEntity.getIdentityType())) {
            LambdaUpdateWrapper<UserProfileEntity> updateWrapper = new LambdaUpdateWrapper<>();
            if (req.getIdentityType() == IdentityType.STUDENT) {
                updateWrapper.eq(UserProfileEntity::getUserId, userId)
                        .set(UserProfileEntity::getAcademicTitle, null);
            } else if (req.getIdentityType() == IdentityType.TEACHER) {
                updateWrapper.eq(UserProfileEntity::getUserId, userId)
                        .set(UserProfileEntity::getMajor, null)
                        .set(UserProfileEntity::getClassName, null)
                        .set(UserProfileEntity::getEnrollmentYear, null)
                        .set(UserProfileEntity::getDegreeLevel, null);
            }
            userProfileMapper.update(null, updateWrapper);
        }

        BeanUtil.copyProperties(req, userEntity);
        userMapper.updateById(userEntity);
    }

    @Override
    public PageResult<UserEntity> getUserListAdmin(int page, int size, String keyword, Status status, IdentityType identityType) {
        page = Math.max(1, page);
        size = Math.max(1, size);

        // 构建查询条件
        LambdaQueryWrapper<UserEntity> queryWrapper = Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getDelFlag, 0);
        if (status != null) {
            queryWrapper.eq(UserEntity::getStatus, status);
        }
        if (identityType != null) {
            queryWrapper.eq(UserEntity::getIdentityType, identityType);
        }

        if (StrUtil.isNotBlank(keyword)) {
            // 关键词模糊匹配 realName，或精确匹配 campusNo、username，或尝试作为 id 精确匹配
            String kw = keyword.trim();
            queryWrapper.and(wrapper -> {
                wrapper.like(UserEntity::getRealName, kw).or().eq(UserEntity::getCampusNo, kw).or().eq(UserEntity::getUsername, kw);
                wrapper.or().eq(UserEntity::getUserId, Long.valueOf(kw));
            });
        }
        queryWrapper.orderByDesc(UserEntity::getCreateTime);

        Page<UserEntity> result = userMapper.selectPage(new Page<>(page, size), queryWrapper);

        List<UserEntity> records = result.getRecords();
        for (UserEntity userEntity : records) {
            userEntity.setPassword(null);
        }
        PageResult<UserEntity> pageResult = new PageResult<>(result.getTotal(), page, size);
        pageResult.addAll(records);
        return pageResult;
    }

    public UserProfileEntity getUserDetailInfoAdmin(Long userId) {
        return userProfileMapper.selectById(userId);
    }
}
