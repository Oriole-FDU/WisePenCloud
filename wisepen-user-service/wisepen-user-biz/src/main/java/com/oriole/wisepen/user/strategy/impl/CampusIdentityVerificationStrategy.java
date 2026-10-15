package com.oriole.wisepen.user.strategy.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.campus.extension.api.domain.dto.CampusIdentityProviderDescriptorDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.CampusIdentityTaskResultDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.VerifiedIdentityClaimsDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.CampusIdentityChallengeSubmitRequest;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.CampusIdentityInitiateRequest;
import com.oriole.wisepen.campus.extension.api.enums.CampusIdentityTaskState;
import com.oriole.wisepen.campus.extension.api.feign.RemoteIdentityService;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.mq.UserTaskCompleteMessage;
import com.oriole.wisepen.user.api.enums.DegreeLevel;
import com.oriole.wisepen.user.api.enums.GenderType;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.domain.entity.UserProfileEntity;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.mapper.UserProfileMapper;
import com.oriole.wisepen.user.mq.KafkaUserEventPublisher;
import com.oriole.wisepen.user.service.IUserInviteService;
import com.oriole.wisepen.user.strategy.UserVerificationStrategy;
import com.oriole.wisepen.user.strategy.campus.CampusIdentityProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampusIdentityVerificationStrategy implements UserVerificationStrategy {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final KafkaUserEventPublisher kafkaUserEventPublisher;
    private final RedisCacheManager redisCacheManager;
    private final IUserInviteService userInviteService;

    private final CampusIdentityProviderRegistry providerRegistry;

    @Override
    public UserVerificationMode getMode() {
        return UserVerificationMode.CAMPUS_IDENTITY_PROVIDER;
    }

    @Override
    public List<String> getReadonlyFields() {
        return Arrays.asList(
                "username", "realName", "campusNo", "email", "mobile", "userStatus",
                "sex", "university", "college", "major",
                "className", "enrollmentYear", "degreeLevel", "inviteCode"
        );
    }

    public List<CampusIdentityProviderDescriptorDTO> listProviders() {
        List<CampusIdentityProviderDescriptorDTO> providers = new ArrayList<>();
        for (String providerCode : providerRegistry.getEnabledProviderCodes()) {
            RemoteIdentityService provider = providerRegistry.require(providerCode);
            CampusIdentityProviderDescriptorDTO descriptor = requireRemoteData(
                    provider.getProviderDescriptor(),
                    UserError.VERIFICATION_CAMPUS_IDENTITY_PROVIDER_NOT_FOUND
            );
            if (!providerCode.equals(descriptor.getProviderCode())) {
                throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_PROVIDER_NOT_FOUND);
            }
            providers.add(descriptor);
        }
        return providers;
    }


    public CampusIdentityTaskResultDTO initiateCampusIdentityVerification(Long userId, String providerCode, Map<String, Object> payload) {
        UserEntity currentUser = userMapper.selectById(userId);
        validateCampusIdentityVerificationState(currentUser, userId, providerCode);

        CampusIdentityInitiateRequest request = CampusIdentityInitiateRequest.builder()
                .userId(userId)
                .providerCode(providerCode)
                .payload(payload)
                .build();
        RemoteIdentityService provider = providerRegistry.require(providerCode);
        return requireRemoteData(
                provider.initiateVerifyTask(request),
                UserError.VERIFICATION_CAMPUS_IDENTITY_REQUEST_FAILED
        );
    }

    public CampusIdentityTaskResultDTO submitLoginChallenge(Long userId, String providerCode, String taskId,
                                                            String challengeId, Map<String, Object> payload) {
        UserEntity currentUser = userMapper.selectById(userId);
        validateCampusIdentityVerificationState(currentUser, userId, providerCode);

        CampusIdentityChallengeSubmitRequest request = CampusIdentityChallengeSubmitRequest.builder()
                .userId(userId)
                .providerCode(providerCode)
                .taskId(taskId)
                .challengeId(challengeId)
                .payload(payload)
                .build();
        RemoteIdentityService provider = providerRegistry.require(providerCode);
        return requireRemoteData(
                provider.submitLoginChallenge(request),
                UserError.VERIFICATION_CAMPUS_IDENTITY_REQUEST_FAILED
        );
    }

    /** 按独立任务 ID 查询 Provider 任务；成功时将标准 claims 一次性写入用户域。 */
    @Transactional(rollbackFor = Exception.class)
    public CampusIdentityTaskResultDTO checkCampusIdentity(Long userId, String providerCode, String taskId) {
        UserEntity currentUser = userMapper.selectById(userId);
        validateCampusIdentityVerificationState(currentUser, userId, providerCode);

        RemoteIdentityService provider = providerRegistry.require(providerCode);
        CampusIdentityTaskResultDTO taskResult = requireRemoteData(
                provider.checkVerifyTaskStatus(taskId, userId),
                UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED
        );
        if (!providerCode.equals(taskResult.getProviderCode())
                || (taskId != null && !taskId.equals(taskResult.getTaskId()))
                || !userId.equals(taskResult.getUserId())) {
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED, "校园身份认证任务归属不一致");
        }
        if (taskResult.getState() == CampusIdentityTaskState.SUCCESS) {
            completeCampusIdentityVerification(userId, providerCode, taskResult);
        }
        return taskResult;
    }

    private void completeCampusIdentityVerification(Long userId, String providerCode, CampusIdentityTaskResultDTO taskResult){
        VerifiedIdentityClaimsDTO claims = taskResult.getClaims();
        if (claims == null) {
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED, "校园身份认证资料缺失");
        }
        if (StrUtil.isBlank(claims.getCampusNo()) || StrUtil.isBlank(claims.getRealName())) {
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED, "校园身份认证资料缺少学工号或姓名");
        }

        long existed = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getCampusNo, claims.getCampusNo())
                .eq(UserEntity::getUserStatus, UserStatus.NORMAL)
                .ne(UserEntity::getUserId, userId));
        if (existed > 0) {
            log.warn("campus identity verify rejected for bound campus number. userId={} providerCode={} campusNo={}",
                    userId, providerCode, claims.getCampusNo());
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_NO_ALREADY_EXISTS);
        }

        IdentityType identityType = claims.getIdentityType() == null ? IdentityType.STUDENT : claims.getIdentityType();
        boolean teacherProfile = identityType == IdentityType.TEACHER;

        UserEntity userEntity = UserEntity.builder()
                .userId(userId).identityType(identityType)
                .campusNo(claims.getCampusNo()).realName(claims.getRealName())
                .mobile(claims.getMobile()).email(claims.getEmail())
                .userStatus(UserStatus.NORMAL)
                .verificationMode(UserVerificationMode.CAMPUS_IDENTITY_PROVIDER)
                .verificationProviderCode(providerCode).build();


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

        redisCacheManager.updateUserStatusInSession(userId, UserStatus.NORMAL);

        if (teacherProfile) {
            redisCacheManager.updateUserIdentityTypeInSession(userId, IdentityType.TEACHER);
            kafkaUserEventPublisher.publishUserTaskComplete(UserTaskCompleteMessage.builder()
                    .userId(userId)
                    .taskCode(UserTaskCode.TEACHER_VERIFICATION)
                    .operatorId(userId)
                    .meta("校园教师认证赠送")
                    .build());
        } else {
            kafkaUserEventPublisher.publishUserTaskComplete(UserTaskCompleteMessage.builder()
                    .userId(userId)
                    .taskCode(UserTaskCode.STUDENT_VERIFICATION)
                    .operatorId(userId)
                    .meta("校园学生认证赠送")
                    .build());
        }
        userInviteService.rewardInviterAfterVerification(userId);

        log.info("campus identity verify succeeded. userId={} providerCode={} campusNo={} teacherProfile={}",
                userId, providerCode, claims.getCampusNo(), teacherProfile);
    }

    /** 仅未认证用户可以发起新的校园身份认证 */
    private void validateCampusIdentityVerificationState(UserEntity userEntity, Long userId, String providerCode) {
        if (userEntity == null
                || userEntity.getUserStatus() != UserStatus.UNIDENTIFIED
                || userEntity.getVerificationMode() != null) {
            log.warn("campus identity verification skipped. userId={} providerCode={}", userId, providerCode);
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID);
        }
    }

    private <T> T requireRemoteData(R<T> response, UserError error) {
        if (response == null || !Integer.valueOf(200).equals(response.getCode())) {
            if (response != null && StrUtil.isNotBlank(response.getMsg())) throw new ServiceException(error, response.getMsg());
            throw new ServiceException(error);
        }
        if (response.getData() == null) throw new ServiceException(error);
        return response.getData();
    }
}
