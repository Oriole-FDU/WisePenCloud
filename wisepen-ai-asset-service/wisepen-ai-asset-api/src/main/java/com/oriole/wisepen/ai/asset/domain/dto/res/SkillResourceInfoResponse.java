package com.oriole.wisepen.ai.asset.domain.dto.res;

import com.oriole.wisepen.ai.asset.domain.base.AIResourceInfoBase;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SkillResourceInfoResponse {
    ResourceItemResponse resourceInfo;
    AIResourceInfoBase skillInfo;
}
