package com.oriole.wisepen.ai.asset.domain.dto.res;

import com.oriole.wisepen.ai.asset.domain.base.AgentInfoBase;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResourceInfoResponse {
    ResourceItemResponse resourceInfo;
    AgentInfoBase agentInfo;
}
