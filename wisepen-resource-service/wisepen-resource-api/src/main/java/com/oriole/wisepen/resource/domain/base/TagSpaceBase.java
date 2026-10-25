package com.oriole.wisepen.resource.domain.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TagSpaceBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "标签空间所属小组 ID")
    private String groupId; // 隔离不同用户组的 Tag 树
}
