package com.oriole.wisepen.resource.domain.base;

import com.oriole.wisepen.resource.enums.VisibilityModeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class TagInfoBase extends TagSpaceBase{
    private String tagName;
    private String tagDesc;
    // 权限配置
    private VisibilityModeEnum visibilityMode;
    private List<String> specifiedUsers; // 配合白名单/黑名单使用的 userId 列表
}