package com.oriole.wisepen.user.api.validation;

import com.oriole.wisepen.user.api.constant.UserRegexPatterns;
import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * 用户名校验器
 *
 * @author Oriole
 */
public class ValidUsernameValidator implements ConstraintValidator<ValidUsername, String> {

    private static final Pattern USERNAME_PATTERN = Pattern.compile(UserRegexPatterns.USERNAME_PATTERN);
    private static final Pattern ELEVEN_DIGIT_PATTERN = Pattern.compile(UserRegexPatterns.ELEVEN_DIGIT_PATTERN);
    private static final Pattern XH_PATTERN = Pattern.compile(UserRegexPatterns.XH_PATTERN);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }

        // 1. 如果符合“11位数字”或“XH格式”，我们认为这是“已存在”的非法格式（模拟学号/手机号占用）
        if (ELEVEN_DIGIT_PATTERN.matcher(value).matches() || XH_PATTERN.matcher(value).matches()) {
            // 禁用默认的 message
            context.disableDefaultConstraintViolation();
            // 设置新的自定义 message
            context.buildConstraintViolationWithTemplate(UserValidationMsg.USERNAME_EXISTED)
                    .addConstraintViolation();
            return false;
        }

        // 2. 检查是否符合基础用户名格式（字母数字下划线等）
        if (!USERNAME_PATTERN.matcher(value).matches()) {
            // 这里不禁用默认，直接返回 false 就会触发注解里的 UserValidationMsg.USERNAME_INVALID
            return false;
        }

        return true;
    }
}