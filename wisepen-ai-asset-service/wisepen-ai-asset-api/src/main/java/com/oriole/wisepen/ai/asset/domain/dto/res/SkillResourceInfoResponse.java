package com.oriole.wisepen.ai.asset.domain.dto.res;

import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SkillResourceInfoResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源信息")
    private ResourceItemResponse resourceInfo;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Skill 元信息")
    private AIResourceMetaInfoResponse skillInfo;
}
