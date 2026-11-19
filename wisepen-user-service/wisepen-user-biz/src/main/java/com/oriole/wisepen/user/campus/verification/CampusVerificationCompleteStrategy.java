package com.oriole.wisepen.user.campus.verification;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.campus.extension.api.domain.dto.VerifiedIdentityClaimsDTO;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.enums.DegreeLevel;
import com.oriole.wisepen.user.api.enums.GenderType;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.campus.IdentityClaimsApplier;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.domain.entity.UserProfileEntity;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.mapper.UserProfileMapper;
import com.oriole.wisepen.user.service.IUserInviteService;
import com.oriole.wisepen.user.strategy.verification.UserVerificationCompleteStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampusVerificationCompleteStrategy
        implements UserVerificationCompleteStrategy<UserVerificationCompleteStrategy.CampusProviderCompleteCommand> {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final RedisCacheManager redisCacheManager;
    private final IUserInviteService userInviteService;
    private final IdentityClaimsApplier identityClaimsApplier;

    @Override
    public List<String> getReadonlyFields() {
        return Arrays.asList(
                "username", "realName", "campusNo", "email", "mobile", "userStatus",
                "sex", "university", "college", "major",
                "className", "enrollmentYear", "degreeLevel", "inviteCode"
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeVerification(UserVerificationCompleteStrategy.CampusProviderCompleteCommand campusProviderCompleteCommand) {
        Long userId = campusProviderCompleteCommand.userId();
        String providerCode = campusProviderCompleteCommand.providerCode();
        VerifiedIdentityClaimsDTO claims = campusProviderCompleteCommand.claims();

        if (claims == null) {
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED, "校园身份认证资料缺失");
        }
        if (StrUtil.isBlank(claims.getCampusNo()) || StrUtil.isBlank(claims.getRealName())) {
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED, "校园身份认证资料缺少学工号或姓名");
        }

        UserEntity currentUser = userMapper.selectById(userId);
        if (currentUser == null
                || currentUser.getUserStatus() != UserStatus.UNIDENTIFIED
                || currentUser.getVerificationMode() != null) {
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID);
        }

        identityClaimsApplier.assertCampusNoAvailable(claims.getCampusNo(), userId, UserError.VERIFICATION_CAMPUS_NO_ALREADY_EXISTS);

        IdentityType identityType = identityClaimsApplier.resolveIdentityType(claims);
        boolean teacherProfile = identityType == IdentityType.TEACHER;

        UserEntity userEntity = identityClaimsApplier.buildVerifiedUserEntity(userId, providerCode, claims);

        UserProfileEntity userProfileEntity = new UserProfileEntity();
        userProfileEntity.setUserId(userId);
        userProfileEntity.setSex(claims.getGender() == null ? GenderType.UNKNOWN : claims.getGender());
        userProfileEntity.setUniversity(claims.getUniversityName());
        userProfileEntity.setCollege(claims.getCollege());
        userProfileEntity.setDegreeLevel(claims.getDegreeLevel() == null ? DegreeLevel.UNKNOWN : claims.getDegreeLevel());

        if (teacherProfile) {
            userProfileEntity.setAcademicTitle(claims.getAcademicTitle());
        } else {
            userProfileEntity.setMajor(claims.getMajor());
            userProfileEntity.setClassName(claims.getClassName());
            userProfileEntity.setEnrollmentYear(claims.getEnrollmentYear());
        }

        if (userMapper.updateById(userEntity) != 1) {
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED, "用户认证状态更新失败");
        }
        if (userProfileMapper.updateById(userProfileEntity) != 1) {
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED, "用户认证资料更新失败");
        }

        redisCacheManager.updateUserStatusInContext(userId, UserStatus.NORMAL);
        identityClaimsApplier.publishVerificationReward(userId, identityType);
        userInviteService.rewardInviterAfterVerification(userId);

        log.info("campus identity claims applied. userId={} providerCode={} campusNo={} teacherProfile={}",
                userId, providerCode, claims.getCampusNo(), teacherProfile);
    }
}
