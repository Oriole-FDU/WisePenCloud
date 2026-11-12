package com.oriole.wisepen.user.api.validation;

import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import com.oriole.wisepen.user.api.domain.dto.req.UserRegisterReqDTO;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

public class ValidUserRegistrationValidator
        implements ConstraintValidator<ValidUserRegistration, UserRegisterReqDTO> {

    @Override
    public boolean isValid(UserRegisterReqDTO user, ConstraintValidatorContext context) {
        if (user == null) {
            return true;
        }

        boolean campusNoPresent = StringUtils.hasText(user.getCampusNo());
        boolean emailPresent = StringUtils.hasText(user.getEmail());
        UserVerificationMode verificationMode = user.getVerificationMode();

        if (campusNoPresent
                && (verificationMode == null || user.getUserStatus() == UserStatus.UNIDENTIFIED)) {
            return reject(context, UserValidationMsg.CAMPUS_NO_REQUIRES_VERIFICATION);
        }
        if (user.getUserStatus() == UserStatus.NORMAL && verificationMode == null) {
            return reject(context, UserValidationMsg.VERIFIED_ACCOUNT_REQUIRES_MODE);
        }
        if (verificationMode != null && user.getUserStatus() == UserStatus.UNIDENTIFIED) {
            return reject(context, UserValidationMsg.VERIFICATION_MODE_REQUIRES_VERIFIED_STATUS);
        }
        if (verificationMode == UserVerificationMode.CAMPUS_IDENTITY_PROVIDER
                && (!campusNoPresent || !emailPresent)) {
            return reject(context, UserValidationMsg.CAMPUS_VERIFICATION_REQUIRES_CAMPUS_NO_AND_EMAIL);
        }
        if (verificationMode == UserVerificationMode.EDU_EMAIL && !emailPresent) {
            return reject(context, UserValidationMsg.EMAIL_VERIFICATION_REQUIRES_EMAIL);
        }
        if (StringUtils.hasText(user.getVerificationProviderCode())
                && verificationMode != UserVerificationMode.CAMPUS_IDENTITY_PROVIDER) {
            return reject(context, UserValidationMsg.VERIFICATION_PROVIDER_REQUIRES_CAMPUS_MODE);
        }
        return true;
    }

    private boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
