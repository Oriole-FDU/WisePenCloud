package com.oriole.wisepen.user.strategy.verification;

import com.oriole.wisepen.campus.extension.api.domain.dto.VerifiedIdentityClaimsDTO;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;

import java.util.List;

/**
 * 用户身份验证完成策略接口
 */
public interface UserVerificationCompleteStrategy<C extends UserVerificationCompleteStrategy.VerificationCompleteCommand> {

    sealed interface VerificationCompleteCommand
            permits EmailCompleteCommand, CampusProviderCompleteCommand {

        UserVerificationMode mode();
    }

    record EmailCompleteCommand(String token) implements VerificationCompleteCommand {
        @Override
        public UserVerificationMode mode() {
            return UserVerificationMode.EDU_EMAIL;
        }
    }

    record CampusProviderCompleteCommand(Long userId, String providerCode, VerifiedIdentityClaimsDTO claims) implements VerificationCompleteCommand {
        @Override
        public UserVerificationMode mode() {
            return UserVerificationMode.CAMPUS_IDENTITY_PROVIDER;
        }
    }

    void completeVerification(C command);

    /**
     * 该策略强接管、禁止用户手动修改的字段名（后端更新过滤 + 前端只读控制共用）
     */
    List<String> getReadonlyFields();
}