package com.oriole.wisepen.user.strategy.login;

import com.oriole.wisepen.user.campus.strategy.CampusClassicAuthInitStrategy;
import com.oriole.wisepen.user.campus.strategy.CampusOAuth2InitStrategy;
import com.oriole.wisepen.user.campus.login.CampusLoginCompleteStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserLoginService {
    
    private final CampusOAuth2InitStrategy campusOAuth2InitStrategy;
    private final CampusClassicAuthInitStrategy campusClassicAuthInitStrategy;
    private final CampusLoginCompleteStrategy campusLoginCompleteStrategy;

    public Object initLogin(UserLoginInitStrategy.LoginInitCommand command) {
        Objects.requireNonNull(command, "command");
        return switch (command) {
            case UserLoginInitStrategy.CampusProviderOAuthInitCommand c -> campusOAuth2InitStrategy.initLogin(c);
            case UserLoginInitStrategy.CampusProviderClassicAuthInitCommand c -> campusClassicAuthInitStrategy.initLogin(c);
        };
    }

    public String completeLogin(UserLoginCompleteStrategy.LoginCompleteCommand command) {
        Objects.requireNonNull(command, "command");
        return switch (command) {
            case UserLoginCompleteStrategy.CampusProviderCompleteCommand c -> campusLoginCompleteStrategy.completeLogin(c);
        };
    }
}