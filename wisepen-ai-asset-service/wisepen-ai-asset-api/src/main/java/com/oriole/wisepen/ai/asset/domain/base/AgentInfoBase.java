package com.oriole.wisepen.ai.asset.domain.base;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public class AgentInfoBase {
    private String name;
    private String description;
    private Integer version;
}
