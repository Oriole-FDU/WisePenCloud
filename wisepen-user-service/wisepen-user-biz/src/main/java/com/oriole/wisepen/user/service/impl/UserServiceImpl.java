package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import com.oriole.wisepen.system.api.feign.RemoteMailService;
import com.oriole.wisepen.user.api.config.UserProperties;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserInfoBase;
import com.oriole.wisepen.user.api.domain.base.UserProfileBase;
import com.oriole.wisepen.user.api.domain.dto.req.*;
import com.oriole.wisepen.user.api.domain.dto.res.UserDetailInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.res.UserSearchUserResponse;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.GroupMemberEntity;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.domain.entity.UserProfileEntity;
import com.oriole.wisepen.user.domain.entity.UserWalletEntity;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.user.mapper.UserWalletsMapper;
import com.oriole.wisepen.user.service.IUserService;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.mapper.UserProfileMapper;
import com.oriole.wisepen.user.strategy.UserVerificationStrategy;
import com.oriole.wisepen.user.strategy.VerificationStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final GroupMemberMapper groupMemberMapper;
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
    public Map<Long, UserDisplayBase> getUserDisplayInfoByIds(Set<Long> userIds) {
        return getUserDisplayInfoByIds(userIds, false);
    }

    @Override
    public Map<Long, UserDisplayBase> getUserDisplayInfoByIds(Set<Long> userIds, boolean includePrivateFields) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        List<UserEntity> userList = userMapper.selectBatchIds(userIds);

        if (CollectionUtils.isEmpty(userList)) {
            return Collections.emptyMap();
        }

        return userList.stream().filter(Objects::nonNull).collect(Collectors.toMap(
                UserEntity::getUserId,
                user -> {
                    UserDisplayBase response = BeanUtil.copyProperties(user, UserDisplayBase.class);
                    if (!includePrivateFields) {
                        response.setRealName(null);
                        response.setCampusNo(null);
                        response.setEmail(null);
                        response.setMobile(null);
                    }
                    return response;
                },
                (existing, replacement) -> existing));
    }

    @Override
    public List<UserSearchUserResponse> searchUser(String keyword) {
        String searchKeyword = keyword.trim();
        LambdaQueryWrapper<UserEntity> userWrapper = Wrappers.<UserEntity>lambdaQuery();
        // 被搜索的用户必须是已经认证的用户
        userWrapper.eq(UserEntity::getUserStatus, UserStatus.NORMAL).isNotNull(UserEntity::getVerificationMode)
                .and(wrapper -> wrapper.eq(UserEntity::getUsername, searchKeyword)
                        .or().eq(UserEntity::getRealName, searchKeyword)
                        .or().eq(UserEntity::getEmail, searchKeyword)
                        .or().eq(UserEntity::getCampusNo, searchKeyword));
        List<UserEntity> candidateUsers = userMapper.selectList(userWrapper);
        if (CollectionUtils.isEmpty(candidateUsers)) {
            return Collections.emptyList();
        }
        return candidateUsers.stream()
                .map(user -> BeanUtil.copyProperties(user, UserSearchUserResponse.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<UserSearchUserResponse> listUserSearchSuggestions(Set<Long> groupIds, String keyword, Integer size) {
        String searchKeyword = keyword.trim();
        if (CollectionUtils.isEmpty(groupIds) || searchKeyword.length() < 2) {
            return Collections.emptyList();
        }
        int querySize = Math.min(Math.max(size == null ? 10 : size, 1), 20);

        LambdaQueryWrapper<GroupMemberEntity> groupMemberWrapper = Wrappers.<GroupMemberEntity>lambdaQuery();
        groupMemberWrapper.in(GroupMemberEntity::getGroupId, groupIds)
                .select(GroupMemberEntity::getUserId);
        Set<Long> groupUserIds = groupMemberMapper.selectList(groupMemberWrapper).stream()
                .map(GroupMemberEntity::getUserId)
                .collect(Collectors.toSet());
        if (CollectionUtils.isEmpty(groupUserIds)) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<UserEntity> userWrapper = Wrappers.<UserEntity>lambdaQuery();
        userWrapper.in(UserEntity::getUserId, groupUserIds)
                .eq(UserEntity::getUserStatus, UserStatus.NORMAL)
                .isNotNull(UserEntity::getVerificationMode)
                .and(wrapper -> wrapper.likeRight(UserEntity::getUsername, searchKeyword)
                        .or().likeRight(UserEntity::getRealName, searchKeyword)
                        .or().likeRight(UserEntity::getEmail, searchKeyword)
                        .or().likeRight(UserEntity::getCampusNo, searchKeyword))
                .orderByAsc(UserEntity::getUsername);
        Page<UserEntity> suggestionPage = userMapper.selectPage(new Page<>(1, querySize, false), userWrapper);

        return suggestionPage.getRecords().stream()
                .map(user -> BeanUtil.copyProperties(user, UserSearchUserResponse.class))
                .collect(Collectors.toList());
    }

    @Override
    public void register(AuthRegisterRequest req) {
        // 校验用户名是否存在
        if (userMapper
                .selectCount(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, req.getUsername())) > 0) {
            throw new ServiceException(UserError.USERNAME_ALREADY_EXISTS);
        }

        // 新建未验证的学生用户
        UserEntity user = UserEntity.builder()
                .username(req.getUsername())
                .nickname(req.getUsername())
                .identityType(IdentityType.STUDENT)
                .userStatus(UserStatus.UNIDENTIFIED)
                .build();

        // 加密用户密码
        user.setPassword(BCrypt.hashpw(req.getPassword()));
        userMapper.insert(user);

        // 新建档案
        UserProfileEntity userProfile = UserProfileEntity.builder()
                .userId(user.getUserId())
                .build();
        userProfileMapper.insert(userProfile);

        UserWalletEntity userWallets = UserWalletEntity.builder().userId(user.getUserId())
                .tokenBalance(0).tokenUsed(0).coinBalance(0).build();
        userWalletsMapper.insert(userWallets);
    }

    @Override
    public void sendResetMail(AuthPwdResetVerifyRequest req) {
        String username = req.getUsername();
        UserEntity userEntity = userMapper
                .selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, username).last("LIMIT 1"));

        if (userEntity == null) {
            log.warn("password reset mail skipped for unknown user. username={}", username);
            return; // 处于安全考虑，不存在也不报错，防止撞库
        }
        // uid存入Redis
        String token = redisCacheManager.setPwdResetToken(userEntity.getUserId());
        // 构建重置链接
        String resetLink = userProperties.getApiDomain() + "/password/reset?token=" + token;

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
            log.info("password reset mail sent. username={} email={}", username, userEntity.getEmail());
        } catch (Exception e) {
            log.error("password reset mail send failed. username={} email={}",
                    username, userEntity.getEmail(), e);
            throw new ServiceException(UserError.USER_PASSWORD_RESET_EMAIL_SEND_FAILED);
        }
    }

    @Override
    public UserDetailInfoResponse getUserInfoById(Long userId) {
        UserEntity userEntity = userMapper.selectById(userId);
        UserProfileEntity userProfileEntity = userProfileMapper.selectById(userEntity.getUserId());

        // 组装响应
        UserDetailInfoResponse res = new UserDetailInfoResponse();
        res.setUserId(userId);
        res.setUserInfo(BeanUtil.copyProperties(userEntity, UserInfoBase.class));
        res.setUserProfile(BeanUtil.copyProperties(userProfileEntity, UserProfileBase.class));

        // 如果已验证
        if (userEntity.getUserStatus() != UserStatus.UNIDENTIFIED) {
            // 设置只读字段
            List<String> readonlyFields = strategyFactory.getStrategy(userEntity.getVerificationMode())
                    .getReadonlyFields();
            res.setReadonlyFields(readonlyFields);
        }
        return res;
    }

    @Override
    public void resetPasswordAdmin(AuthPwdAdminResetRequest req) {
        Long userId = req.getUserId();
        updatePasswordByUserId(userId,
                StrUtil.isBlank(req.getNewPassword()) ? userProperties.getDefaultPassword() : req.getNewPassword());
        log.info("password reset. userId={} mode=admin", userId);
    }

    @Override
    public void resetPassword(AuthPwdResetRequest req) {
        Long userId = redisCacheManager.getPwdResetUser(req.getToken());
        if (userId == null) {
            throw new ServiceException(UserError.USER_PASSWORD_RESET_EXPIRED);
        }

        updatePasswordByUserId(userId, req.getNewPassword());
        log.info("password reset. userId={} mode=self", userId);
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
        if (userEntity.getUserStatus() == UserStatus.UNIDENTIFIED) {
            // 未通过身份认证，不能更新Profile
            throw new ServiceException(UserError.CANNOT_OPERATE_BEFORE_AUTH_VERIFICATION);
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
        boolean authContextChanged = (req.getIdentityType() != null && !req.getIdentityType().equals(userEntity.getIdentityType()))
                || (req.getUserStatus() != null && !req.getUserStatus().equals(userEntity.getUserStatus()));

        // 唯一性校验 username
        if (req.getUsername() != null && !req.getUsername().equals(userEntity.getUsername())) {
            if (userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                    .eq(UserEntity::getUsername, req.getUsername())
                    .ne(UserEntity::getUserId, userId)) > 0) {
                throw new ServiceException(UserError.USERNAME_ALREADY_EXISTS);
            }
        }
        // 唯一性校验 campusNo
        if (req.getCampusNo() != null && !req.getCampusNo().equals(userEntity.getCampusNo())) {
            if (userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                    .eq(UserEntity::getCampusNo, req.getCampusNo())
                    .eq(UserEntity::getUserStatus, UserStatus.NORMAL)
                    .ne(UserEntity::getUserId, userId)) > 0) {
                throw new ServiceException(UserError.CAMPUS_NO_ALREADY_EXISTS);
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
        if (authContextChanged) {
            redisCacheManager.deleteSessionsByUserId(userId);
        }
    }

    @Override
    public PageR<UserEntity> getUserListAdmin(int page, int size, String keyword, UserStatus userStatus,
            IdentityType identityType) {
        page = Math.max(1, page);
        size = Math.max(1, size);

        // 构建查询条件
        LambdaQueryWrapper<UserEntity> queryWrapper = Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getDelFlag, 0);
        if (userStatus != null) {
            queryWrapper.eq(UserEntity::getUserStatus, userStatus);
        }
        if (identityType != null) {
            queryWrapper.eq(UserEntity::getIdentityType, identityType);
        }

        if (StrUtil.isNotBlank(keyword)) {
            // 关键词模糊匹配 realName，或精确匹配 campusNo、username，或尝试作为 id 精确匹配
            String kw = keyword.trim();
            queryWrapper.and(wrapper -> {
                wrapper.like(UserEntity::getRealName, kw).or().eq(UserEntity::getCampusNo, kw).or()
                        .eq(UserEntity::getUsername, kw);
                wrapper.or().eq(UserEntity::getUserId, Long.valueOf(kw));
            });
        }
        queryWrapper.orderByDesc(UserEntity::getCreateTime);

        Page<UserEntity> result = userMapper.selectPage(new Page<>(page, size), queryWrapper);

        List<UserEntity> records = result.getRecords();
        for (UserEntity userEntity : records) {
            userEntity.setPassword(null);
        }
        PageR<UserEntity> pageR = new PageR<>(result.getTotal(), page, size);
        pageR.addAll(records);
        return pageR;
    }

    public UserProfileEntity getUserDetailInfoAdmin(Long userId) {
        return userProfileMapper.selectById(userId);
    }
}
