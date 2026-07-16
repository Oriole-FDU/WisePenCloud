package com.oriole.wisepen.system.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 反馈操作类型枚举
 * 记录反馈状态变更时的操作类型
 * 
 * @author Architecture Team
 */
@Getter
@AllArgsConstructor
public enum OperationType {
    CREATED(1, "CREATED", "创建反馈"),
    STATUS_CHANGED(2, "STATUS_CHANGED", "状态变更"),
    ASSIGNED(3, "ASSIGNED", "分配处理人"),
    RESOLVED(4, "RESOLVED", "标记已解决"),
    CLOSED(5, "CLOSED", "关闭反馈"),
    IGNORED(6, "IGNORED", "忽略反馈"),
    COMMENT_ADDED(7, "COMMENT_ADDED", "添加回复");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
    
    private final String label;

    public static boolean isValid(String value) {
        for (OperationType type : OperationType.values()) {
            if (type.value.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
