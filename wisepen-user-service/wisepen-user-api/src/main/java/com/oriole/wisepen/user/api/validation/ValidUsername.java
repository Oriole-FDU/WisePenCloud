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

    /**
     * 是否将特殊格式（11位数字、3位数字+XH+4位数字）视为"已存在"
     * 如果为true，这些格式会返回"用户名已存在"而不是"格式不正确"
     */
    boolean treatSpecialFormatAsExisting() default false;

    /**
     * 特殊格式的错误消息
     */
    String specialFormatMessage() default UserValidationMsg.USERNAME_EXISTED;

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}