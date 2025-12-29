package com.oriole.wisepen.user.api.validation;

import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 学工号校验注解
 *
 * @author Oriole
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidCampusNoValidator.class)
public @interface ValidCampusNo {
    String message() default UserValidationMsg.CAMPUS_NO_INVALID;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}