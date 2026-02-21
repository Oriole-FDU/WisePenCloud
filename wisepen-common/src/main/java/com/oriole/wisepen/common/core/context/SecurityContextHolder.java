package com.oriole.wisepen.common.core.context;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.alibaba.ttl.TransmittableThreadLocal;
import cn.hutool.core.convert.Convert;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.oriole.wisepen.common.core.constant.SecurityConstants;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.security.exception.PermissionErrorCode;
import com.oriole.wisepen.common.core.domain.enums.ResultCode;
import com.oriole.wisepen.common.security.exception.PermissionException;
import com.oriole.wisepen.common.core.exception.ServiceException;

/**
 * 核心安全上下文
 * 用于在当前线程中存储从网关透传过来的用户信息
 */
public class SecurityContextHolder {

    private static final TransmittableThreadLocal<Map<String, Object>> THREAD_LOCAL = new TransmittableThreadLocal<>();

    // 设置值
    public static void set(String key, Object value) {
        Map<String, Object> map = THREAD_LOCAL.get();
        if (map == null) {
            map = new ConcurrentHashMap<>();
            THREAD_LOCAL.set(map);
        }
        map.put(key, value);
    }

    // 获取值并转换为指定类型
    public static <T> T get(String key, Class<T> clazz) {
        Map<String, Object> map = THREAD_LOCAL.get();
        return map == null ? null : Convert.convert(clazz, map.get(key));
    }

    // 设置当前用户ID
    public static void setUserId(String userId) { set(SecurityConstants.HEADER_USER_ID, userId); }

    // 获取当前用户ID
    public static String getUserId() { return get(SecurityConstants.HEADER_USER_ID, String.class); }

    // 设置用户身份类型
    public static void setIdentityType(Integer code) { set(SecurityConstants.HEADER_IDENTITY_TYPE, IdentityType.getByCode(code)); }

    // 获取用户身份类型 (1:学生 2:老师 3:管理员)
    public static IdentityType getIdentityType() { return get(SecurityConstants.HEADER_IDENTITY_TYPE, IdentityType.class); }

    // 设置用户所在的Group与Role
    public static void setGroupRoleMap(String groupRoleMapJson) {
        Map<String, Integer> rawMap = JSONUtil.toBean(groupRoleMapJson, new TypeReference<Map<String, Integer>>() {}, false);
        if (CollUtil.isNotEmpty(rawMap)) {
            Map<String, GroupRoleType> typedMap = rawMap.entrySet().stream().map(e -> new AbstractMap.SimpleEntry<>(
                    e.getKey(),
                    GroupRoleType.getByCode(e.getValue())
            )).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
            set(SecurityConstants.HEADER_GROUP_ROLE_MAP, typedMap);
        }
    }

    // 获取用户所在的Group与Role
    @SuppressWarnings("unchecked")
    public static Map<String, GroupRoleType> getGroupRoleMap() {
        Map<String, GroupRoleType> map = get(SecurityConstants.HEADER_GROUP_ROLE_MAP, Map.class);
        return map != null ? map : Collections.emptyMap();
    }

    public static GroupRoleType getGroupRole(String targetGroupId) {
        if (targetGroupId == null) {
            return GroupRoleType.NOT_MEMBER;
        }
        GroupRoleType groupRole = getGroupRoleMap().get(targetGroupId);
        return groupRole != null ? groupRole : GroupRoleType.NOT_MEMBER;
    }

    public static void assertInGroup(String targetGroupId) {
        GroupRoleType currentRole = getGroupRole(targetGroupId);
        if (currentRole == GroupRoleType.NOT_MEMBER){
            throw new PermissionException(PermissionErrorCode.OPERATION_UNAUTHORIZED);
        }
    }

    public static void assertGroupRole(String targetGroupId, List<GroupRoleType> requiredRoles) {
        if (targetGroupId == null || CollUtil.isEmpty(requiredRoles)) {
            throw new ServiceException(ResultCode.SYSTEM_ERROR);
        }
        GroupRoleType currentRole = getGroupRole(targetGroupId);
        if (!requiredRoles.contains(currentRole)) {
            throw new PermissionException(PermissionErrorCode.OPERATION_UNAUTHORIZED);
        }
    }

    public static void assertGroupRole(String targetGroupId, GroupRoleType... requiredRoles) {
        assertGroupRole(targetGroupId, Arrays.asList(requiredRoles));
    }

    // 清理上下文 (必须在拦截器结束时调用)
    public static void remove() {
        THREAD_LOCAL.remove();
    }
}