package com.oriole.wisepen.ai.asset.domain.base;

import com.oriole.wisepen.ai.asset.enums.AIResourceSourceType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * skill / agent 资源主档的公共信息字段
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class AIResourceInfoBase {
    private String name;
    private String description;
    private Integer version;
    // skill 来源类型；agent 不使用，恒为 null
    private AIResourceSourceType sourceType;
}
