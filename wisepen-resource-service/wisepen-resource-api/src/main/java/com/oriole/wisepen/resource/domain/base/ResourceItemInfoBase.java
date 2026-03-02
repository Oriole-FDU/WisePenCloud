package com.oriole.wisepen.resource.domain.base;

import lombok.Data;

@Data
public class ResourceItemInfoBase {
    private String resourceName;      // 资源名称/标题
    private String resourceType;      // 资源类型 (NOTE, DOC, PDF)
    private String ownerId;           // 所有者
    private String preview;           // 可选：预览图
    private Long size;                // 可选：文件大小/字数摘要
}