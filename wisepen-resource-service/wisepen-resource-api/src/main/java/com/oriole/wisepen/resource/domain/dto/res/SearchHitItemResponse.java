package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 单条搜索命中结果 DTO。
 * <p>
 * 字段命名对齐 {@code ResourceItemInfoBase.resourceName} / {@code ResourceItemEntity.resourceType}，
 * 使列表页和搜索页在前端处理同一种数据形状。{@code resourceName} 与 {@code highlightContent}
 * 可能携带高亮包裹标签（详见 {@link com.oriole.wisepen.resource.constant.SearchConstants}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHitItemResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String resourceId;

    /** 资源类型，复用枚举：序列化后是 "pdf"/"note" 等小写字符串 */
    private ResourceType resourceType;

    /** 资源名称，可能包含高亮包裹标签 */
    private String resourceName;

    /** 正文片段高亮，可能为 null（命中只发生在标题时） */
    private String highlightContent;

    private LocalDateTime updateTime;

    private String tags;
}
