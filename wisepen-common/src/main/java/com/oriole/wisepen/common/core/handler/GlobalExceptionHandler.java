package com.oriole.wisepen.common.core.handler;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.ResultCode;
import com.oriole.wisepen.common.security.exception.PermissionException;
import com.oriole.wisepen.common.core.exception.ServiceException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.text.MessageFormat;

@Slf4j
@RestControllerAdvice // 拦截所有 Controller
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final Environment environment;

    public GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String errorMessage = e.getMessage();
        if (body instanceof ProblemDetail problemDetail){
            errorMessage = MessageFormat.format("{0}({1})", problemDetail.getTitle(), problemDetail.getDetail());
        }
        log.error("系统内部错误(Spring), Status={}, Msg={}", status.value(), errorMessage, e);
        R<Void> customBody = R.fail(status.value(), "系统内部错误: " + errorMessage);
        return new ResponseEntity<>(customBody, headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String errorMsg = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("请求参数错误: {}", errorMsg);
        R<Void> customBody = R.fail(status.value(), "参数错误: " + errorMsg);
        return new ResponseEntity<>(customBody, headers, status);
    }

    // 捕获业务异常 (ServiceException)
    @ExceptionHandler(ServiceException.class)
    public R<Void> handleServiceException(ServiceException e, HttpServletResponse response) {
        response.setStatus(e.getCode() == HttpServletResponse.SC_INTERNAL_SERVER_ERROR ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR: HttpServletResponse.SC_OK);
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    // 捕获权限异常 (PermissionException)
    @ExceptionHandler(PermissionException.class)
    public R<Void> handlePermissionException(PermissionException e, HttpServletResponse response) {
        response.setStatus(e.getCode());
        log.warn("权限异常: type={}, msg={}", e.getType(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    //兜底异常
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletResponse response) {
        log.error("系统内部错误", e);

        // 原始异常
        int httpStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        String errorMessage = String.format("System Error (%s): %s", e.getClass().getSimpleName(), e.getMessage());
        response.setStatus(httpStatus);

        // 判断当前是否处于 'dev' 或 'test' 环境
        boolean isDev = environment.acceptsProfiles(Profiles.of("dev", "test"));
        if (isDev) {
            return R.fail(ResultCode.SYSTEM_ERROR, errorMessage);
        } else {
            return R.fail(ResultCode.SYSTEM_ERROR);
        }
    }
}