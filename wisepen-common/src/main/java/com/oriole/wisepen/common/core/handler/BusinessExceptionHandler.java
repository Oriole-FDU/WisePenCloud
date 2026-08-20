package com.oriole.wisepen.common.core.handler;

import com.oriole.wisepen.common.core.constant.CommonError;
import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.security.exception.PermissionError;
import com.oriole.wisepen.common.security.exception.PermissionException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice // 拦截所有 Controller
public class BusinessExceptionHandler {

    private final Environment environment;

    public BusinessExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    // 捕获业务异常 (ServiceException)
    @ExceptionHandler(ServiceException.class)
    public R<Void> handleServiceException(ServiceException e, HttpServletResponse response) {
        IResult errorResult = e.getErrorResult();
        response.setStatus(HttpServletResponse.SC_OK);
        log.warn("business exception handled. code={} msgKey={} msg={}",
                errorResult.getCode(), errorResult.getKey(), e.getMessage());
        return R.fail(errorResult, e.getMessage());
    }

    // 捕获权限异常 (PermissionException)
    @ExceptionHandler(PermissionException.class)
    public R<Void> handlePermissionException(PermissionException e, HttpServletResponse response) {
        IResult errorResult = e.getErrorResult();
        int httpStatus = switch (errorResult) {
            case PermissionError.UNAUTHORIZED,
                 PermissionError.ACCOUNT_UNIDENTIFIED,
                 PermissionError.PERMISSION_DENIED -> HttpServletResponse.SC_FORBIDDEN;
            default -> HttpServletResponse.SC_UNAUTHORIZED;
        };
        response.setStatus(httpStatus);
        log.warn("permission exception handled. code={} msgKey={} msg={}",
                errorResult.getCode(), errorResult.getKey(), e.getMessage());
        return R.fail(errorResult, e.getMessage());
    }

    //兜底异常
    @ExceptionHandler(Exception.class)
    public R<Map<String, Object>> handleException(Exception e, HttpServletResponse response) {
        log.error("internal error handled. exception={}", e.getClass().getSimpleName(), e);

        // 原始异常
        int httpStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        response.setStatus(httpStatus);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("class", e.getClass().getSimpleName());

        // 判断当前是否处于 'dev' 或 'test' 环境
        boolean isDev = environment.acceptsProfiles(Profiles.of("dev", "test"));
        if (isDev) {
            return R.fail(CommonError.INTERNAL_ERROR, e.getMessage(), detail);
        } else {
            return R.fail(CommonError.INTERNAL_ERROR);
        }
    }
}
