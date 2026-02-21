package com.oriole.wisepen.common.web.interceptor;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.constant.CommonConstants;
import com.oriole.wisepen.common.core.constant.SecurityConstants;
import com.oriole.wisepen.common.core.context.GrayContextHolder;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;

import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.file.AccessDeniedException;

public class HeaderInterceptor implements HandlerInterceptor {

    private final String fromSource;

    public HeaderInterceptor(String fromSource) {
        this.fromSource = fromSource;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 在这里校验一下 "X-From-Source"，防止外网绕过网关直接攻击微服务端口
        if (!fromSource.equals(request.getHeader(SecurityConstants.HEADER_FROM_SOURCE))) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return false;
        }

        // 从 Header 中获取 APISIX 透传的明文信息
        String userIdStr = request.getHeader(SecurityConstants.HEADER_USER_ID);
        String identityTypeStr = request.getHeader(SecurityConstants.HEADER_IDENTITY_TYPE);
        String groupRoleMapJson = request.getHeader(SecurityConstants.HEADER_GROUP_ROLE_MAP);

        // 如果 Header 里有 UserID，说明网关已认证通过
        if (StrUtil.isNotBlank(userIdStr)) {
            SecurityContextHolder.setUserId(userIdStr);

            if (StrUtil.isNotBlank(identityTypeStr)) {
                SecurityContextHolder.setIdentityType(Integer.parseInt(identityTypeStr));
            }
            if (StrUtil.isNotBlank(groupRoleMapJson)) {
                SecurityContextHolder.setGroupRoleMap(groupRoleMapJson);
            }
        }

         // 灰度发布标记
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