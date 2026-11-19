package com.oriole.wisepen.user.campus.strategy;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.campus.extension.api.domain.dto.ClassicAuthProviderDescriptorDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.ClassicAuthTaskResultDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.VerifiedIdentityClaimsDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.ClassicAuthChallengeSubmitRequest;
import com.oriole.wisepen.campus.extension.api.enums.ClassicAuthTaskState;
import com.oriole.wisepen.campus.extension.api.feign.RemoteClassicAuthService;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.campus.CampusClassicAuthProviderRegistry;
import com.oriole.wisepen.user.campus.CampusAuthProtocol;
import com.oriole.wisepen.user.campus.CampusProviderProperties;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.strategy.login.UserLoginCompleteStrategy;
import com.oriole.wisepen.user.strategy.login.UserLoginService;
import com.oriole.wisepen.user.strategy.verification.UserVerificationCompleteStrategy;
import com.oriole.wisepen.user.strategy.verification.UserVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampusClassicAuthFlowStrategy {

    private final UserMapper userMapper;
    private final CampusClassicAuthProviderRegistry providerRegistry;
    private final CampusProviderProperties properties;
    private final UserLoginService userLoginService;
    private final UserVerificationService userVerificationService;

    /** 列出已启用经典身份认证的 Provider 描述。 */
    public List<ClassicAuthProviderDescriptorDTO> listProviders() {
        List<ClassicAuthProviderDescriptorDTO> providers = new ArrayList<>();
        for (String providerCode : providerRegistry.getEnabledProviderCodes()) {
            RemoteClassicAuthService provider = providerRegistry.require(providerCode);
            ClassicAuthProviderDescriptorDTO descriptor = requireRemoteData(
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

    /** 向 Provider 提交登录 Challenge 响应 */
    public ClassicAuthTaskResultDTO submitLoginChallenge(Long userId, String providerCode, String taskId,
                                                         String challengeId, Map<String, String> answers) {
        validateCampusIdentityVerificationState(userId, providerCode);
        ClassicAuthChallengeSubmitRequest request = ClassicAuthChallengeSubmitRequest.builder()
                .userId(userId).providerCode(providerCode).taskId(taskId).challengeId(challengeId).answers(answers).build();
        RemoteClassicAuthService provider = providerRegistry.require(providerCode);
        return requireRemoteData(provider.submitLoginChallenge(request), UserError.VERIFICATION_CAMPUS_IDENTITY_REQUEST_FAILED);
    }

    /** 轮询身份认证任务；成功时把标准 claims 交给身份认证完成策略落库 */
    public ClassicAuthTaskResultDTO checkCampusIdentity(Long userId, String providerCode, String taskId) {
        validateCampusIdentityVerificationState(userId, providerCode);
        RemoteClassicAuthService provider = providerRegistry.require(providerCode);
        ClassicAuthTaskResultDTO taskResult = requireRemoteData(
                provider.checkVerifyTaskStatus(taskId, userId),
                UserError.VERIFICATION_CAMPUS_IDENTITY_FAILED
        );
        if (taskResult.getState() == ClassicAuthTaskState.SUCCESS) {
            userVerificationService.completeVerification(
                    new UserVerificationCompleteStrategy.CampusProviderCompleteCommand(userId, providerCode, taskResult.getClaims())
            );
        }
        return taskResult;
    }

    private void validateCampusIdentityVerificationState(Long userId, String providerCode) {
        UserEntity userEntity = userMapper.selectById(userId);
        if (userEntity == null
                || userEntity.getUserStatus() != UserStatus.UNIDENTIFIED
                || userEntity.getVerificationMode() != null) {
            log.warn("campus identity verification skipped. userId={} providerCode={}", userId, providerCode);
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID);
        }
    }

    /** 轮询登录任务；成功时建立会话并返回 sessionId，处理中返回 null */
    public String checkLogin(String providerCode, String taskId) {
        properties.requireLoginProtocol(providerCode, CampusAuthProtocol.CLASSIC, UserError.CLASSIC_LOGIN_PROVIDER_NOT_FOUND);
        RemoteClassicAuthService provider = providerRegistry.require(providerCode);
        ClassicAuthTaskResultDTO taskResult = requireRemoteData(
                provider.checkVerifyTaskStatus(taskId, null),
                UserError.CLASSIC_LOGIN_FAILED
        );
        if (taskResult.getState() == ClassicAuthTaskState.SUCCESS) {
            VerifiedIdentityClaimsDTO claims = taskResult.getClaims();
            if (claims == null || StrUtil.isBlank(claims.getCampusNo())) {
                throw new ServiceException(UserError.CLASSIC_LOGIN_FAILED, "校园用户信息缺少学工号");
            }
            return userLoginService.completeLogin(
                    new UserLoginCompleteStrategy.CampusProviderCompleteCommand(providerCode, claims)
            );
        }
        return null;
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
