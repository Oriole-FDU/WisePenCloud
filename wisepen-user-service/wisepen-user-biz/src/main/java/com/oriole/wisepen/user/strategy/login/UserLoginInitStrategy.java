package com.oriole.wisepen.user.strategy.login;

import java.util.Map;

/**
 * 用户登录初始化策略接口
 */
public interface UserLoginInitStrategy<I extends UserLoginInitStrategy.LoginInitCommand, T> {

    sealed interface LoginInitCommand
            permits CampusProviderOAuthInitCommand, CampusProviderClassicAuthInitCommand {
    }

    record CampusProviderOAuthInitCommand(String providerCode) implements LoginInitCommand {
    }

    record CampusProviderClassicAuthInitCommand(String providerCode, Map<String, String> inputs) implements LoginInitCommand {
    }

    T initLogin(I command);
}