package com.oriole.wisepen.user.api.constant;

/**
 * 用户校验消息常量接口
 * 用于统一存储校验相关的消息常量，避免硬编码
 *
 */
public interface UserValidationMsg {

    /**
     * 用户名相关消息
     */
    String USERNAME_INVALID = "用户名必须是4-20位字母、数字或下划线";
    String USERNAME_EMPTY = "用户名不能为空";
<<<<<<< HEAD
<<<<<<< HEAD
    String USERNAME_EXISTED = "用户名已存在";
=======
>>>>>>> 0c5e42f (feat(): 新增了注册、找回密码、重置密码相关功能，位于user-service的userService)
=======
    String USERNAME_EXISTED = "用户名已存在";
>>>>>>> a72a49c (fix():将注册，密码找回逻辑移回/auth,解决了/user路径需要JWT Token的问题)

    /**
     * 密码相关消息
     */
    String PASSWORD_INVALID = "密码长度必须大于8位且包含字母和数字";
    String PASSWORD_EMPTY = "密码不能为空";

    /**
     * 学工号相关消息
     */
    String CAMPUS_NO_INVALID = "学工号格式不正确";
    String CAMPUS_NO_EMPTY = "学工号不能为空";
}