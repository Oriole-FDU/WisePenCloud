package com.oriole.wisepen.ai.asset.domain.base;

import com.oriole.wisepen.ai.asset.enums.AIResourceSourceType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@SuperBuilder
@NoArgsConstructor
public class AIResourceInfoBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "AI 资源名称")
    private String name;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "AI 资源描述")
    private String description;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "当前发布版本号")
    private Integer version;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源来源类型")
    private AIResourceSourceType sourceType;
}
