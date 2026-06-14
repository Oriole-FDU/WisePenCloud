package com.oriole.wisepen.ai.asset.domain.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Agent 运行配置，对齐 py 端 chat-service agents/models.py 的 AgentSpec
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSpecInfoBase {

    private String systemPrompt;

    private Boolean autoGenerateTitle;

    private AgentModelPolicy modelPolicy;

    private AgentToolAndSkillPolicy toolAndSkillPolicy;

    private AgentMemoryPolicy memoryPolicy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentModelPolicy {
        private String defaultModelId;
        private String defaultProviderId;
        private Boolean allowRequestOverride;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentToolAndSkillPolicy {
        private Boolean enableUseTool;
        private Set<String> allowToolNames;
        private Set<String> denyToolNames;
        private Boolean enableUseSkill;
        private Set<String> onDemandSkillIds;
        private Set<String> forceEnabledSkillIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentMemoryPolicy {
        private Boolean enableChatMemory;
        private Boolean enablePersistenceChatMemory;
        private Boolean enableChatMemorySummary;
        private Double highWatermarkRatio;
        private Double lowWatermarkRatio;
        private String summaryPrompt;
        private Boolean enableLongTermMemory;
        private Integer longTermMemoryLimit;
        private Double longTermMemoryScoreThreshold;
    }
}
