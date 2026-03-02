package com.oriole.wisepen.common.security.annotation;

import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import java.lang.annotation.*;

/**
 * 全局角色（身份）校验注解
 * 支持传入多个角色，默认逻辑为 OR（满足其一即可放行）
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CheckRole {

    IdentityType[] value();
}