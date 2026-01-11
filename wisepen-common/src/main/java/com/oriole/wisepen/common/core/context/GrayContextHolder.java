package com.oriole.wisepen.common.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import org.springframework.util.StringUtils;

import java.util.Map;

public class GrayContextHolder {
    // 存储当前线程的开发者标识
    private static final TransmittableThreadLocal<String> DEVELOPER_TAG_CTX = new TransmittableThreadLocal<>();

    private static String PROCESS_DEFAULT_TAG;

    public static void setDeveloperTag(String tag) {
        DEVELOPER_TAG_CTX.set(tag);
    }

    public static String getDeveloperTag() {
        // 尝试拿线程上下文 (Controller 传下来的)
        String tag = DEVELOPER_TAG_CTX.get();
        if (StringUtils.hasText(tag)) {
            return tag;
        }
        // 降级使用当前进程的默认身份
        return PROCESS_DEFAULT_TAG;
    }

    public static void clear() {
        DEVELOPER_TAG_CTX.remove();
    }

    // 启动时设置一次即可
    public static void setProcessDefaultTag(String tag) {
        PROCESS_DEFAULT_TAG = tag;
    }
}