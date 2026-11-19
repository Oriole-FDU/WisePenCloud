package com.oriole.wisepen.user.strategy.login;

import com.oriole.wisepen.campus.extension.api.domain.dto.VerifiedIdentityClaimsDTO;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;

import java.util.List;

/**
 * 用户登录完成策略接口
 */
public interface UserLoginCompleteStrategy<C extends UserLoginCompleteStrategy.LoginCompleteCommand> {

    sealed interface LoginCompleteCommand permits CampusProviderCompleteCommand {
    }

    record CampusProviderCompleteCommand(String providerCode, VerifiedIdentityClaimsDTO claims) implements LoginCompleteCommand {
    }

    String completeLogin(C command);
}