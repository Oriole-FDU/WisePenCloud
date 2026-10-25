package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.domain.base.TagInfoBase;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ResourceTagBindResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "小组 ID")
    private String groupId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "主标签 ID")
    private String primaryTagId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "标签 ID 到标签信息的映射")
    private Map<String, TagInfoBase> tags = new LinkedHashMap<>();
}
