package com.oriole.wisepen.user.api.constant;

/**
 * 用户校验消息常量接口
 * 用于统一存储校验相关的消息常量，避免硬编码
 *
 */
public interface UserValidationMsg {

    String USER_ID_EMPTY = "用户ID不能为空";

    /**
     * 用户名相关消息
     */
    String USERNAME_INVALID = "用户名必须是 4-20 位字母、数字或下划线";
    String USERNAME_EMPTY = "用户名不能为空";
    String USERNAME_INVALID_SPECIAL_FORMAT= "用户名不能使用系统禁止的特殊格式";

    /**
     * 密码相关消息
     */
    String PASSWORD_INVALID = "密码长度必须大于8位且包含字母和数字";
    String PASSWORD_EMPTY = "密码不能为空";
    String CURRENT_PASSWORD_EMPTY = "旧密码不能为空";
    String RESET_PWD_TOKEN_EMPTY = "重置密码的令牌不能为空";

    /**
     * 学工号相关消息
     */
    String CAMPUS_NO_INVALID = "学工号格式不正确";
    String CAMPUS_NO_EMPTY = "学工号不能为空";

    /**
     * 批量注册用户资料相关消息
     */
    String EMAIL_INVALID = "邮箱格式不正确";
    String MOBILE_INVALID = "手机号格式不正确";
    String USER_ID_INVALID = "用户ID必须为正数";
    String CAMPUS_NO_REQUIRES_VERIFICATION = "指定学工号时必须指定认证方式，且账号不能为未认证状态";
    String VERIFIED_ACCOUNT_REQUIRES_MODE = "已认证账号必须指定认证方式";
    String VERIFICATION_MODE_REQUIRES_VERIFIED_STATUS = "指定认证方式时账号不能为未认证状态";
    String CAMPUS_VERIFICATION_REQUIRES_CAMPUS_NO_AND_EMAIL = "校园身份认证必须同时指定学工号和邮箱";
    String EMAIL_VERIFICATION_REQUIRES_EMAIL = "邮箱认证必须指定邮箱";
    String VERIFICATION_PROVIDER_REQUIRES_CAMPUS_MODE = "认证 Provider 编码仅适用于校园身份认证";
}
