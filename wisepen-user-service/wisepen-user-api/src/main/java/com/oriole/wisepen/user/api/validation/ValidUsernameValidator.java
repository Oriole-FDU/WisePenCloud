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

    private boolean treatSpecialFormatAsExisting;
    private String specialFormatMessage;

    @Override
    public void initialize(ValidUsername constraintAnnotation) {
        this.treatSpecialFormatAsExisting = constraintAnnotation.treatSpecialFormatAsExisting();
        this.specialFormatMessage = constraintAnnotation.specialFormatMessage();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // @NotBlank会处理null和空字符串，这里只处理格式验证
        if (value == null || value.trim().isEmpty()) {
            return true; // 让@NotBlank处理空值
        }

        // 检查是否为特殊格式
        boolean isSpecialFormat = isSpecialFormat(value);

        if (isSpecialFormat) {
            if (treatSpecialFormatAsExisting) {
                // 如果配置为将特殊格式视为已存在，则禁用默认消息并使用自定义消息
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(specialFormatMessage)
                       .addConstraintViolation();
            }
            return false; // 特殊格式总是不合法
        }

        // 检查是否匹配合法格式
        return USERNAME_PATTERN.matcher(value).matches();
    }

    /**
     * 检查用户名是否为特殊格式（11位数字或3位数字+XH+4位数字）
     * 这些格式虽然不合法，但需要特殊处理返回"用户名已存在"
     */
    public static boolean isSpecialFormat(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return ELEVEN_DIGIT_PATTERN.matcher(username).matches() ||
               XH_PATTERN.matcher(username).matches();
    }
}