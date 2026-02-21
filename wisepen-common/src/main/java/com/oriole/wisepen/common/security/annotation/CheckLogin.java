package com.oriole.wisepen.common.security.annotation;

import java.lang.annotation.*;

/**
 * 登录校验注解
 * 贴上此注解的接口/类，必须在 Header 中存在合法的 userId 才能访问
 */
@Target({ElementType.METHOD, ElementType.TYPE}) // 支持贴在方法上，也支持贴在整个 Controller 类上
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CheckLogin {
}