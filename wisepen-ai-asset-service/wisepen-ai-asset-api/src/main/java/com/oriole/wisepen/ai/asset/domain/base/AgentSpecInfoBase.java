package com.oriole.wisepen.ai.asset.domain.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

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
        private Boolean toolSelectionDefaultEnabled;
        private Map<String, Boolean> toolSelectionOverrides;
        private Boolean enableUseSkill;
        private Set<String> onDemandSkillIds;
        private Integer skillMatchTopK;
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
