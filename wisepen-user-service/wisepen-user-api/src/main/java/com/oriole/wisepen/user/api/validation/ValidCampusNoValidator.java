package com.oriole.wisepen.user.api.validation;

import com.oriole.wisepen.user.api.constant.UserRegexPatterns;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * 学工号校验器
 *
 * @author Oriole
 */
public class ValidCampusNoValidator implements ConstraintValidator<ValidCampusNo, String> {

    private static final Pattern CAMPUS_NO_PATTERN = Pattern.compile(UserRegexPatterns.CAMPUS_NO_PATTERN);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // @NotBlank会处理null和空字符串，这里只处理格式验证
        if (value == null || value.trim().isEmpty()) {
            return true; // 让@NotBlank处理空值
        }
        return CAMPUS_NO_PATTERN.matcher(value).matches();
    }
}