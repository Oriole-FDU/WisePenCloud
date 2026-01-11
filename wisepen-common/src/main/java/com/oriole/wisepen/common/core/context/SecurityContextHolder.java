package com.oriole.wisepen.common.core.context;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.ttl.TransmittableThreadLocal;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.oriole.wisepen.common.core.constant.SecurityConstants;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;

/**
 * 核心安全上下文
 * 用于在当前线程中存储从网关透传过来的用户信息
 * 使用 TransmittableThreadLocal 以支持父子线程传递
 */
public class SecurityContextHolder {

    private static final TransmittableThreadLocal<Map<String, Object>> THREAD_LOCAL = new TransmittableThreadLocal<>();

    /**
     * 设置值
     */
    public static void set(String key, Object value) {
        Map<String, Object> map = THREAD_LOCAL.get();
        if (map == null) {
            map = new ConcurrentHashMap<>();
            THREAD_LOCAL.set(map);
        }
        map.put(key, value);
    }

    /**
     * 获取值
     */
    public static Object get(String key) {
        Map<String, Object> map = THREAD_LOCAL.get();
        if (map == null) {
            return null;
        }
        return map.get(key);
    }

    /**
     * 获取值并转换为指定类型
     */
    public static <T> T get(String key, Class<T> clazz) {
        Map<String, Object> map = THREAD_LOCAL.get();
        return map == null ? null : Convert.convert(clazz, map.get(key));
    }

    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        return get(SecurityConstants.HEADER_USER_ID, Long.class);
    }

    /**
     * 设置当前用户ID
     */
    public static void setUserId(Long userId) {
        set(SecurityConstants.HEADER_USER_ID, userId);
    }

    /**
     * 获取用户身份类型 (1:学生 2:老师 3:管理员)
     */
    public static IdentityType getIdentityType() {
        Integer code = get(SecurityConstants.HEADER_IDENTITY_TYPE, Integer.class);
        return IdentityType.getByCode(code); // 🔥 自动转成枚举
    }

    /**
     * 设置用户身份类型
     */
    public static void setIdentityType(IdentityType identityType) {
        set(SecurityConstants.HEADER_IDENTITY_TYPE, identityType.getCode());
    }

    // 保留接收 Integer 的方法，给拦截器用
    public static void setIdentityType(Integer code) {
        set(SecurityConstants.HEADER_IDENTITY_TYPE, code);
    }

    /**
     * 获取用户所在的 Group IDs (逗号分隔字符串)
     */
    public static List<Long> getGroupIds() {
        // 从 Map 中取出来就是 List<Long> (因为在 set 时已经转好了)
        return get(SecurityConstants.HEADER_GROUP_IDS, List.class);
    }

    public static void setGroupIds(String groupIdsStr) {
        if (StrUtil.isNotBlank(groupIdsStr)) {
            // Hutool 自动识别逗号分隔，并转为 Long 类型集合
            List<Long> list = Convert.toList(Long.class, groupIdsStr);
            set(SecurityConstants.HEADER_GROUP_IDS, list);
        }
    }

    public static boolean isMemberOfGroup(Long targetGroupId) {
        if (targetGroupId == null) {
            return false;
        }
        List<Long> groupIds = getGroupIds();
        return CollUtil.contains(groupIds, targetGroupId);
    }

    /**
     * 获取当前请求的来源 (如: Gateway)
     */
    public static String getFromSource() {
        return get(SecurityConstants.HEADER_FROM_SOURCE, String.class);
    }

    /**
     * 清理上下文 (必须在拦截器结束时调用)
     */
    public static void remove() {
        THREAD_LOCAL.remove();
    }
}