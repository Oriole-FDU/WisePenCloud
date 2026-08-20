package com.oriole.wisepen.resource.enums;

/**
 * 资源标签绑定变更模式。
 */
public enum ResourceTagUpdateMode {
    /**
     * 使用请求中的标签列表全量替换当前空间下的资源标签绑定
     */
    REPLACE,

    /**
     * 在当前空间下追加请求中的标签绑定
     */
    ADD,

    /**
     * 从当前空间下移除请求中的标签绑定
     */
    REMOVE
}
