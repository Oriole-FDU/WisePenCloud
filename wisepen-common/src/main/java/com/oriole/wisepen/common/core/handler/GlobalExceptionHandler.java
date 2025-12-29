package com.oriole.wisepen.common.core.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.ResultCode;
import com.oriole.wisepen.common.core.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.List;

@Slf4j
@RestControllerAdvice // 拦截所有 Controller
public class GlobalExceptionHandler {

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
        // 获取所有字段错误
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        StringBuilder errorMsg = new StringBuilder();
        for (FieldError error : fieldErrors) {
            errorMsg.append(error.getField()).append(": ").append(error.getDefaultMessage()).append("; ");
        }
        String message = errorMsg.toString();
        log.warn("参数校验失败: {}", message);
        return R.fail(ResultCode.PARAM_ERROR.getCode(), message);
    }
    /**
     * 兜底异常，防止未知的空指针等错误直接把堆栈暴露给前端
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统未知错误", e);
        // 开发模式下返回更详细的错误信息
        String errorMessage = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "无详细错误信息");
        return R.fail(ResultCode.SYSTEM_ERROR.getCode(), errorMessage);
    }
    //@ExceptionHandler()
}