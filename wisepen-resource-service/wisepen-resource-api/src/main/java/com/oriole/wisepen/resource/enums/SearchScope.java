package com.oriole.wisepen.resource.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 搜索范围枚举，与前端"全部 / 文档 / 笔记"三 Tab 一一对应。
 * <p>
 * {@code DOCUMENT} 抽象的是"除笔记以外所有有实体文件的资源类型"：未来新增 EPUB/TXT 等
 * 仅需扩展 {@link ResourceType}，本枚举与 {@link #includedResourceTypes()} 不需要修改，
 * 前端 Tab 也不必感知底层文件格式列表。
 */
public enum SearchScope {

    /** 不加类型过滤，覆盖全部资源类型 */
    ALL,

    /** 笔记之外、有实体文件的资源类型集合 */
    DOCUMENT,

    /** 仅笔记类型 */
    NOTE;

    /**
     * 将当前范围展开为具体的 {@link ResourceType} 列表，供 ES 过滤器构建侧使用。
     * <p>
     * 始终排除 {@link ResourceType#UNKNOWN}：UNKNOWN 仅用于兜底，不应出现在用户可见的搜索结果中。
     */
    public List<ResourceType> includedResourceTypes() {
        return switch (this) {
            case ALL -> Arrays.stream(ResourceType.values())
                    .filter(t -> t != ResourceType.UNKNOWN)
                    .toList();
            case NOTE -> List.of(ResourceType.NOTE);
            case DOCUMENT -> Arrays.stream(ResourceType.values())
                    .filter(t -> t != ResourceType.NOTE && t != ResourceType.UNKNOWN)
                    .toList();
        };
    }
}
