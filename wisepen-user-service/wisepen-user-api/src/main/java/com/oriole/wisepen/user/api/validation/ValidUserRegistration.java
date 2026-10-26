package com.oriole.wisepen.user.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidUserRegistrationValidator.class)
public @interface ValidUserRegistration {
    String message() default "用户认证信息不一致";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
