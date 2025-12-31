package com.oriole.wisepen.common.core.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.ResultCode;
import com.oriole.wisepen.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice // 拦截所有 Controller
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Environment environment;

    /**
     * 捕获业务异常 (ServiceException)，这是我们自己主动抛出的，不仅有 code 还有 msg，直接透传给前端
     */
    @ExceptionHandler(ServiceException.class)
    public R<Void> handleServiceException(ServiceException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 捕获 Sa-Token 未登录异常
     */
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLoginException(NotLoginException e) {
        // 打印一下具体原因（比如是 token 无效，还是 token 过期，还是被踢下线）
        log.warn("认证失败: type={}, msg={}", e.getType(), e.getMessage());
        return R.fail(ResultCode.NOT_LOGIN);
    }

    /**
     * 捕获 Sa-Token 无权限异常 (权限码)
     */
    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermissionException(NotPermissionException e) {
        log.warn("权限不足: {}", e.getMessage());
        return R.fail(ResultCode.NO_PERMISSION);
    }

    /**
     * 捕获 Sa-Token 无角色异常 (角色标识)
     */
    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRoleException(NotRoleException e) {
        log.warn("角色不符: {}", e.getMessage());
        return R.fail(ResultCode.NO_ROLE);
    }
    /**
     * 捕获 Bean Validation 参数校验异常（@Valid注解校验失败）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return R.fail(ResultCode.PARAM_ERROR, message);
    }
    /**
     * 兜底异常
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("Unknow System Error", e);
        // 判断当前是否处于 'dev' 或 'test' 环境
        boolean isDev = environment.acceptsProfiles(Profiles.of("dev", "test"));
        if (isDev) {
            String errorMessage = String.format("System Error (%s): %s", e.getClass().getSimpleName(), e.getMessage());
            return R.fail(ResultCode.SYSTEM_ERROR, errorMessage);
        } else {
            return R.fail(ResultCode.SYSTEM_ERROR);
        }
    }
}