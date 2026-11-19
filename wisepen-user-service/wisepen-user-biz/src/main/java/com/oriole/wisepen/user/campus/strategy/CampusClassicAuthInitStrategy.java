package com.oriole.wisepen.user.campus.strategy;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.campus.extension.api.domain.dto.ClassicAuthTaskResultDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.ClassicAuthInitiateRequest;
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
import com.oriole.wisepen.user.strategy.login.UserLoginInitStrategy;
import com.oriole.wisepen.user.strategy.verification.UserVerificationInitStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampusClassicAuthInitStrategy implements
        UserVerificationInitStrategy<UserVerificationInitStrategy.CampusProviderClassicAuthInitCommand, ClassicAuthTaskResultDTO>,
        UserLoginInitStrategy<UserLoginInitStrategy.CampusProviderClassicAuthInitCommand, ClassicAuthTaskResultDTO>
{

    private final UserMapper userMapper;
    private final CampusClassicAuthProviderRegistry providerRegistry;
    private final CampusProviderProperties properties;

    public ClassicAuthTaskResultDTO initLogin(UserLoginInitStrategy.CampusProviderClassicAuthInitCommand command) {
        properties.requireLoginProtocol(command.providerCode(), CampusAuthProtocol.CLASSIC, UserError.CLASSIC_LOGIN_PROVIDER_NOT_FOUND);
        return initAuth(null, command.providerCode(), command.inputs(), UserError.CLASSIC_LOGIN_REQUEST_FAILED);
    }

    public ClassicAuthTaskResultDTO initVerification(UserVerificationInitStrategy.CampusProviderClassicAuthInitCommand command) {
        Long userId = command.userId();
        properties.requireIdentityProtocol(command.providerCode(), CampusAuthProtocol.CLASSIC, UserError.VERIFICATION_CAMPUS_IDENTITY_PROVIDER_NOT_FOUND);
        UserEntity userEntity = userMapper.selectById(userId);
        if (userEntity == null || userEntity.getUserStatus() != UserStatus.UNIDENTIFIED || userEntity.getVerificationMode() != null) {
            log.warn("campus identity verification skipped. userId={} providerCode={}", userId, command.providerCode());
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_IDENTITY_STATE_INVALID);
        }
        return initAuth(userId, command.providerCode(), command.inputs(), UserError.VERIFICATION_CAMPUS_IDENTITY_REQUEST_FAILED);
    }

    private ClassicAuthTaskResultDTO initAuth(Long userId, String providerCode, Map<String, String> inputs, UserError remoteError) {
        ClassicAuthInitiateRequest request = ClassicAuthInitiateRequest.builder()
                .userId(userId).providerCode(providerCode).inputs(inputs).build();
        RemoteClassicAuthService provider = providerRegistry.require(providerCode);
        return requireRemoteData(provider.initiateVerifyTask(request), remoteError);
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
