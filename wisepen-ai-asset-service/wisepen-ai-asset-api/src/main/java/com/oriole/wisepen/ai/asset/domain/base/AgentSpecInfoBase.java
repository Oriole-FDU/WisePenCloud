package com.oriole.wisepen.ai.asset.domain.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSpecInfoBase {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "系统提示词")
    private String systemPrompt;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "是否自动生成会话标题")
    private Boolean autoGenerateTitle;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "模型策略")
    private AgentModelPolicy modelPolicy;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "工具与技能策略")
    private AgentToolAndSkillPolicy toolAndSkillPolicy;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "记忆策略")
    private AgentMemoryPolicy memoryPolicy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentModelPolicy {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "默认模型 ID")
        private String defaultModelId;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "默认模型提供方 ID")
        private String defaultProviderId;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "是否允许请求覆盖模型")
        private Boolean allowRequestOverride;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentToolAndSkillPolicy {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "是否启用工具")
        private Boolean enableUseTool;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "工具选择是否默认启用")
        private Boolean toolSelectionDefaultEnabled;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "工具选择覆盖项")
        private Map<String, Boolean> toolSelectionOverrides;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "是否启用技能")
        private Boolean enableUseSkill;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "按需加载的技能 ID 集合")
        private Set<String> onDemandSkillIds;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int32", description = "技能匹配数量上限")
        private Integer skillMatchTopK;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentMemoryPolicy {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "是否启用会话记忆")
        private Boolean enableChatMemory;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "是否持久化会话记忆")
        private Boolean enablePersistenceChatMemory;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "是否启用会话记忆摘要")
        private Boolean enableChatMemorySummary;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "double", description = "摘要触发高水位比例")
        private Double highWatermarkRatio;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "double", description = "摘要回落到低水位比例")
        private Double lowWatermarkRatio;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "摘要提示词")
        private String summaryPrompt;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "是否启用长期记忆")
        private Boolean enableLongTermMemory;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int32", description = "长期记忆条数上限")
        private Integer longTermMemoryLimit;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "double", description = "长期记忆评分阈值")
        private Double longTermMemoryScoreThreshold;
    }
}
