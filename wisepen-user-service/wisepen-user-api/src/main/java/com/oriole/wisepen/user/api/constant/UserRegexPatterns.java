package com.oriole.wisepen.user.api.constant;

/**
 * 用户相关正则表达式常量接口
 * 集中管理所有正则表达式，避免硬编码
 *
 */
public interface UserRegexPatterns {

    /**
     * 用户名正则：4-20位字母、数字或下划线，必须包含字母
     */
    String USERNAME_PATTERN = "^(?=.*[a-zA-Z])[a-zA-Z0-9_]{4,20}$";

    /**
     * 密码正则：大于8位且包含字母和数字
     */
    String PASSWORD_PATTERN = "^(?=.*[a-zA-Z])(?=.*\\d).{8,}$";

    /**
     * 学工号正则：11位数字或5位数字XH4位数字
     */
    String CAMPUS_NO_PATTERN = "^(\\d{11}|\\d{5}XH\\d{4})$";

    /**
     * 邮箱正则：基本邮箱格式验证
     */
    String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    /**
     * 手机号正则：中国手机号格式
     */
    String MOBILE_PATTERN = "^1[3-9]\\d{9}$";

    /**
     * 特殊非法用户名格式（11位数字）
     */
    String ELEVEN_DIGIT_PATTERN = "^\\d{11}$";

    /**
     * 特殊非法用户名格式（5位数字+XH+4位数字）
     */
    String XH_PATTERN = "^\\d{5}XH\\d{4}$";
}