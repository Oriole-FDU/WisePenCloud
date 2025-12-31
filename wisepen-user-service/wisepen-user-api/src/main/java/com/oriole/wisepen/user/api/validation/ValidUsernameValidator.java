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

    // 合法格式：包含字母的字母数字下划线组合（4-20位）
    private static final Pattern USERNAME_PATTERN = Pattern.compile(UserRegexPatterns.USERNAME_PATTERN);

    // 11位数字格式（不合法）
    private static final Pattern ELEVEN_DIGIT_PATTERN = Pattern.compile(UserRegexPatterns.ELEVEN_DIGIT_PATTERN);

    // 3位数字+XH+4位数字格式（不合法）
    private static final Pattern XH_PATTERN = Pattern.compile(UserRegexPatterns.XH_PATTERN);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // @NotBlank会处理null和空字符串，这里只处理格式验证
        if (value == null || value.trim().isEmpty()) {
            return true; // 让@NotBlank处理空值
        }

        return ELEVEN_DIGIT_PATTERN.matcher(value).matches() || XH_PATTERN.matcher(value).matches() || USERNAME_PATTERN.matcher(value).matches();
    }
}