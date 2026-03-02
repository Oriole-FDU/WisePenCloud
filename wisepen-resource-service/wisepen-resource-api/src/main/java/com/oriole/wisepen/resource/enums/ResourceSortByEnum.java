package com.oriole.wisepen.resource.enums;

import lombok.Getter;

@Getter
public enum ResourceSortByEnum {
    UPDATE_TIME("updateTime"),
    CREATE_TIME("createTime"),
    NAME("resourceName"),
    SIZE("size");

    // 对应的 MongoDB 实体字段名
    private final String dbField;

    ResourceSortByEnum(String dbField) {
        this.dbField = dbField;
    }
}
