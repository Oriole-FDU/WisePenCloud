package com.oriole.wisepen.resource.domain.base;

import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceItemInfoBase {
    private String resourceName;      // 资源名称/标题
    private ResourceType resourceType;// 资源类型
    private String ownerId;           // 所有者
    private String preview;           // 可选：预览图
    private Long size;                // 可选：文件大小/字数摘要

    /** 阅读量、点赞、评分、收藏等统计数据 */
    private ResourceInteractionInfoBase resourceInteractionInfo;
}