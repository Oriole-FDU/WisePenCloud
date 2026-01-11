package com.oriole.wisepen.common.security.service;

import cn.dev33.satoken.stp.StpInterface;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限加载接口实现
 * 不查库，直接从 SecurityContextHolder (即 Header) 中获取权限
 */
@Component
public class SaPermissionImpl implements StpInterface {

    /**
     * 获取权限列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 返回空，暂时不做精确的权限管控
        return new ArrayList<>();
    }

    /**
     * 获取角色列表 (e.g., "student", "teacher", "admin")
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 从 ThreadLocal 获取当前用户的身份类型 (Int)
        IdentityType identity = SecurityContextHolder.getIdentityType();

        if (identity == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(identity.getDesc());
    }
}