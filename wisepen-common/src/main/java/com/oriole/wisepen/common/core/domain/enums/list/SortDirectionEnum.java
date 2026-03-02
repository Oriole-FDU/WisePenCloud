package com.oriole.wisepen.common.core.domain.enums.list;

// 通用排序方向枚举
public enum SortDirectionEnum {
    // 正序 (从小到大 / 从旧到新 / A-Z)
    ASC,
    //倒序 (从大到小 / 从新到旧 / Z-A)
    DESC;

    //转换为 Spring Data 的原生 Direction
    public org.springframework.data.domain.Sort.Direction toSpringDirection() {
        return this == ASC ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
    }
}