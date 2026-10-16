package com.oriole.wisepen.generic.resource.api.constant;

import com.oriole.wisepen.resource.enums.ResourceType;

import java.util.Set;

/**
 * 通用资源服务常量：声明本服务托管的资源类型集合。
 */
public class GenericResourceConstants {

    public static final Set<ResourceType> MANAGED_TYPES = Set.of(
            ResourceType.ARCHIVE,
            ResourceType.BINARY,
            ResourceType.UNKNOWN
    );

    private GenericResourceConstants() {
    }
}
