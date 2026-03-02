package com.oriole.wisepen.common.security.aspect;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.common.security.exception.PermissionErrorCode;
import com.oriole.wisepen.common.security.exception.PermissionException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class SecurityAspect {

    /**
     * 拦截所有打了 @CheckLogin 或 @CheckRole 注解的方法或类
     */
    @Before("@within(com.oriole.wisepen.common.security.annotation.CheckLogin) || " +
            "@annotation(com.oriole.wisepen.common.security.annotation.CheckLogin) || " +
            "@within(com.oriole.wisepen.common.security.annotation.CheckRole) || " +
            "@annotation(com.oriole.wisepen.common.security.annotation.CheckRole)")
    public void checkSecurity(JoinPoint joinPoint) {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget().getClass();

        // 获取注解
        CheckRole checkRole = getAnnotation(method, targetClass, CheckRole.class);

        // 执行登录态校验
        String userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new PermissionException(PermissionErrorCode.NOT_LOGIN);
        }

        // 执行角色校验
        if (checkRole != null) {
            IdentityType currentIdentity = SecurityContextHolder.getIdentityType();
            if (currentIdentity == null) {
                throw new PermissionException(PermissionErrorCode.IDENTITY_UNAUTHORIZED);
            }
            // 判断当前身份是否在允许的数组内
            if (!Arrays.asList(checkRole.value()).contains(currentIdentity)) {
                throw new PermissionException(PermissionErrorCode.IDENTITY_UNAUTHORIZED);
            }
        }
    }

    // 优先从方法上获取注解，如果方法上没有，则去类上找
    private <T extends java.lang.annotation.Annotation> T getAnnotation(Method method, Class<?> targetClass, Class<T> annotationClass) {
        T annotation = AnnotationUtils.findAnnotation(method, annotationClass);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(targetClass, annotationClass);
        }
        return annotation;
    }
}