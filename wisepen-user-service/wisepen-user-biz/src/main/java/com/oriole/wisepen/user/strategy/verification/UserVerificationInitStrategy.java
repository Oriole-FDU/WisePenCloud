package com.oriole.wisepen.user.strategy.verification;

import java.util.Map;

/**
 * 用户身份验证初始化策略接口
 */
public interface UserVerificationInitStrategy<I extends UserVerificationInitStrategy.VerificationInitCommand, T> {

    sealed interface VerificationInitCommand
            permits EmailInitCommand, CampusProviderOAuthInitCommand, CampusProviderClassicAuthInitCommand {
    }

    record EmailInitCommand(Long userId, String email) implements VerificationInitCommand {
    }

    record CampusProviderOAuthInitCommand(Long userId, String providerCode) implements VerificationInitCommand {
    }

    record CampusProviderClassicAuthInitCommand(Long userId, String providerCode, Map<String, String> inputs) implements VerificationInitCommand {
    }

    T initVerification(I command);
}