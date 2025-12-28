package com.oriole.wisepen.user.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UsernameValidator.class)
public @interface Username {
    String message() default "用户名必须是4-20位字母、数字或下划线";

    /**
     * 是否将特殊格式（11位数字、3位数字+XH+4位数字）视为"已存在"
     * 如果为true，这些格式会返回"用户名已存在"而不是"格式不正确"
     */
    boolean treatSpecialFormatAsExisting() default false;

    /**
     * 特殊格式的错误消息
     */
    String specialFormatMessage() default "用户名已存在";

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}