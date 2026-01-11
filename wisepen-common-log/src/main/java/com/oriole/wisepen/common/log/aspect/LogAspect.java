package com.oriole.wisepen.common.log.aspect;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.log.service.AsyncLogService;
import com.oriole.wisepen.system.api.domain.dto.SysOperLogDTO;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

import static com.oriole.wisepen.common.core.constant.SecurityConstants.HEADER_USER_ID;

@Aspect
@Component
@Slf4j
public class LogAspect {

    @Autowired
    private AsyncLogService asyncLogService;

    /**
     * 处理完请求后执行
     */
    @AfterReturning(pointcut = "@annotation(controllerLog)", returning = "jsonResult")
    public void doAfterReturning(JoinPoint joinPoint, Log controllerLog, Object jsonResult) {
        handleLog(joinPoint, controllerLog, null, jsonResult);
    }

    /**
     * 拦截异常操作
     */
    @AfterThrowing(value = "@annotation(controllerLog)", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Log controllerLog, Exception e) {
        handleLog(joinPoint, controllerLog, e, null);
    }

    protected void handleLog(final JoinPoint joinPoint, Log controllerLog, final Exception e, Object jsonResult) {
        try {
            // 获取当前请求对象
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

            // 基础信息构建
            SysOperLogDTO operLog = new SysOperLogDTO();
            operLog.setStatus(0);
            operLog.setOperTime(LocalDateTime.now());

            if (request != null) {
                operLog.setOperIp(getIpAddr(request));
                operLog.setOperUrl(request.getRequestURI());
                operLog.setReqMethod(request.getMethod());

                String userId = request.getHeader(HEADER_USER_ID);
                if (StrUtil.isNotBlank(userId)) {
                    operLog.setOperUserId(Long.valueOf(userId));
                }
            }

            // 设置方法和类名
            String className = joinPoint.getTarget().getClass().getName();
            String methodName = joinPoint.getSignature().getName();
            operLog.setMethod(className + "." + methodName + "()");

            // 处理注解参数
            if (controllerLog != null) {
                operLog.setBusinessType(controllerLog.businessType().ordinal());
                operLog.setTitle(controllerLog.title());

                // 保存请求参数
                if (controllerLog.isSaveRequestData() && request != null) {
                    if (HttpMethod.PUT.name().equals(request.getMethod()) || HttpMethod.POST.name().equals(request.getMethod())) {
                        String params = argsArrayToString(joinPoint.getArgs());
                        operLog.setOperParam(params);
                    }
                }
                // 保存响应结果
                if (controllerLog.isSaveResponseData() && ObjectUtil.isNotNull(jsonResult)) {
                    operLog.setJsonResult(JSONUtil.toJsonStr(jsonResult));
                }
            }

            // 异常处理
            if (e != null) {
                operLog.setStatus(1);
                operLog.setErrorMsg(e.getMessage());
            }

            // 调用异步 Service
            asyncLogService.saveSysLog(operLog);

        } catch (Exception exp) {
            log.error("==前置通知异常==");
            log.error("日志记录异常信息:{}", exp.getMessage());
        }
    }

    // 参数拼装辅助方法
    private String argsArrayToString(Object[] paramsArray) {
        try {
            if (paramsArray != null && paramsArray.length > 0) {
                // 过滤掉 request/response 等不能序列化的对象，这里简化处理
                return JSONUtil.toJsonStr(paramsArray);
            }
        } catch (Exception e) {
            // 忽略参数序列化错误
        }
        return "";
    }

    /**
     * 获取客户端真实 IP 地址 (适配 APISIX/Nginx 代理)
     */
    private String getIpAddr(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        // 尝试从 X-Forwarded-For 获取 (APISIX 默认会带这个)
        String ip = request.getHeader("x-forwarded-for");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // 多次反向代理后会有多个ip值，第一个ip是真实ip
            if (ip.contains(",")) {
                ip = ip.split(",")[0];
            }
        }
        // 最后使用 getRemoteAddr (如果没有代理，或者 Header 丢了)
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip;
    }
}