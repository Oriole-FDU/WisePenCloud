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

<<<<<<< HEAD
    private static final Pattern USERNAME_PATTERN = Pattern.compile(UserRegexPatterns.USERNAME_PATTERN);
    private static final Pattern ELEVEN_DIGIT_PATTERN = Pattern.compile(UserRegexPatterns.ELEVEN_DIGIT_PATTERN);
=======
    // 合法格式：包含字母的字母数字下划线组合（4-20位）
    private static final Pattern USERNAME_PATTERN = Pattern.compile(UserRegexPatterns.USERNAME_PATTERN);

    // 11位数字格式（不合法）
    private static final Pattern ELEVEN_DIGIT_PATTERN = Pattern.compile(UserRegexPatterns.ELEVEN_DIGIT_PATTERN);

    // 3位数字+XH+4位数字格式（不合法）
>>>>>>> 0c5e42f (feat(): 新增了注册、找回密码、重置密码相关功能，位于user-service的userService)
    private static final Pattern XH_PATTERN = Pattern.compile(UserRegexPatterns.XH_PATTERN);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
<<<<<<< HEAD
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
=======
        // @NotBlank会处理null和空字符串，这里只处理格式验证
        if (value == null || value.trim().isEmpty()) {
            return true; // 让@NotBlank处理空值
        }

        return ELEVEN_DIGIT_PATTERN.matcher(value).matches() || XH_PATTERN.matcher(value).matches() || USERNAME_PATTERN.matcher(value).matches();
>>>>>>> 0c5e42f (feat(): 新增了注册、找回密码、重置密码相关功能，位于user-service的userService)
    }
}