package com.oriole.wisepen.common.core.domain.enums.list;

//通用查询逻辑枚举 (用于控制多个条件传入时的组合策略)
public enum QueryLogicEnum {
    // 且关系：必须同时满足所有传入的条件 (交集)
    AND,
    // 或关系：满足传入的任意一个条件即可 (并集)
    OR
}
