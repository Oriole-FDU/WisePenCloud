package com.oriole.wisepen.common.web.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.constant.CommonConstants;
import com.oriole.wisepen.common.core.constant.SecurityConstants;
import com.oriole.wisepen.common.core.context.GrayContextHolder;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;

import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class HeaderInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从 Header 中获取 APISIX 透传的明文信息
        String userIdStr = request.getHeader(SecurityConstants.HEADER_USER_ID);
        String identityTypeStr = request.getHeader(SecurityConstants.HEADER_IDENTITY_TYPE);
        String groupIdsStr = request.getHeader(SecurityConstants.HEADER_GROUP_IDS);

        // 如果 Header 里有 UserID，说明网关已认证通过
        if (StrUtil.isNotBlank(userIdStr)) {
            Long userId = Long.parseLong(userIdStr);
            Integer identityType = StrUtil.isNotBlank(identityTypeStr) ? Integer.parseInt(identityTypeStr) : null;

            // 填充自定义上下文
            SecurityContextHolder.setUserId(userId);
            if (StrUtil.isNotBlank(identityTypeStr)) {
                SecurityContextHolder.setIdentityType(Integer.parseInt(identityTypeStr));
            }
            if (StrUtil.isNotBlank(groupIdsStr)) {
                SecurityContextHolder.setGroupIds(groupIdsStr);
            }

            // 注入 Sa-Token 上下文
            // switchTo 不会去查 Redis，只是在当前线程标记用户身份
            // 这样你在 Controller 里依然可以用 @SaCheckPermission，或者 StpUtil.getLoginId()
            StpUtil.switchTo(userId);
        }

        // 在这里校验一下 "X-From-Source"，防止外网绕过网关直接攻击微服务端口
        // if (!"APISIX".equals(request.getHeader(SecurityConstants.HEADER_FROM_SOURCE))) {
        //     throw new AccessDeniedException("非法访问，请通过网关请求");
        // }

        String developer = request.getHeader(CommonConstants.GRAY_HEADER_DEV_KEY);
        if (StringUtils.hasText(developer)) {
            GrayContextHolder.setDeveloperTag(developer);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        GrayContextHolder.clear();
        SecurityContextHolder.remove();
    }
}