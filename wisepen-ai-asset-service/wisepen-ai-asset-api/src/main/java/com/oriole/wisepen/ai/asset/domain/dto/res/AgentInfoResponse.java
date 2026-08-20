package com.oriole.wisepen.ai.asset.domain.dto.res;

import com.oriole.wisepen.ai.asset.domain.base.AIResourceInfoBase;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AgentInfoResponse extends AIResourceInfoBase {
    private String resourceId;
    private AgentVersionBundleInfoResponse agentVersionBundle;
}
