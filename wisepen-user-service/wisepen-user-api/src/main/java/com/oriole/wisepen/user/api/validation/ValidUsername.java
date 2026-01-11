package com.oriole.wisepen.user.api.validation;

import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用户名校验注解
 *
 * @author Oriole
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidUsernameValidator.class)
public @interface ValidUsername {
    String message() default UserValidationMsg.USERNAME_INVALID;

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}