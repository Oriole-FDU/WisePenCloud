package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.resource.domain.base.TagInfoBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class TagTreeResponse extends TagInfoBase {
    private String tagId;
    private String parentId;
    private List<TagTreeResponse> children; // 子节点列表，用于在内存中组装树返回给前端
}