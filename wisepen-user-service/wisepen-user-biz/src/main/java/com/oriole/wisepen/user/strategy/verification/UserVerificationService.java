package com.oriole.wisepen.user.strategy.verification;

import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import com.oriole.wisepen.user.campus.strategy.CampusClassicAuthInitStrategy;
import com.oriole.wisepen.user.campus.strategy.CampusOAuth2InitStrategy;
import com.oriole.wisepen.user.campus.verification.CampusVerificationCompleteStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserVerificationService {

    private final EmailVerificationStrategy emailStrategy;
    private final CampusOAuth2InitStrategy campusOAuth2InitStrategy;
    private final CampusClassicAuthInitStrategy campusClassicAuthInitStrategy;
    private final CampusVerificationCompleteStrategy campusVerificationCompleteStrategy;

    public Object initVerification(UserVerificationInitStrategy.VerificationInitCommand command) {
        Objects.requireNonNull(command, "command");
        return switch (command) {
            case UserVerificationInitStrategy.EmailInitCommand c -> emailStrategy.initVerification(c);
            case UserVerificationInitStrategy.CampusProviderOAuthInitCommand c -> campusOAuth2InitStrategy.initVerification(c);
            case UserVerificationInitStrategy.CampusProviderClassicAuthInitCommand c -> campusClassicAuthInitStrategy.initVerification(c);
        };
    }

    public void completeVerification(UserVerificationCompleteStrategy.VerificationCompleteCommand command) {
        Objects.requireNonNull(command, "command");
        switch (command) {
            case UserVerificationCompleteStrategy.EmailCompleteCommand c -> emailStrategy.completeVerification(c);
            case UserVerificationCompleteStrategy.CampusProviderCompleteCommand c -> campusVerificationCompleteStrategy.completeVerification(c);
        }
    }

    public List<String> getReadonlyFields(UserVerificationMode mode) {
        Objects.requireNonNull(mode, "mode");
        return switch (mode) {
            case EDU_EMAIL -> emailStrategy.getReadonlyFields();
            case CAMPUS_IDENTITY_PROVIDER -> campusVerificationCompleteStrategy.getReadonlyFields();
        };
    }
}