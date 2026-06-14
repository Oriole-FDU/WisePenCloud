package com.oriole.wisepen.ai.asset.domain.entity;

import com.oriole.wisepen.ai.asset.exception.AgentError;
import com.oriole.wisepen.common.core.exception.ServiceException;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@Document(collection = "wisepen_agent_versions")
public class AgentVersionBundleEntity extends BaseVersionBundleEntity {

    // agent 发布要求运行配置中的 system_prompt 非空
    @Override
    public void checkReadyToPublish() {
        if (getSpec() == null || getSpec().getSystemPrompt() == null || getSpec().getSystemPrompt().isBlank()) {
            throw new ServiceException(AgentError.AGENT_PROMPT_REQUIRED);
        }
    }
}
